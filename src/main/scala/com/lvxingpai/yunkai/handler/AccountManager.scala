package com.lvxingpai.yunkai.handler

import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.{ Date, UUID }

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.google.inject.Inject
import com.google.inject.name.Named
import com.lvxingpai.idgen.IdGen
import com.lvxingpai.smscenter.SmsCenter
import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.Implicits.JsonConversions._
import com.lvxingpai.yunkai.Implicits.YunkaiConversions._
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.formatter.UserInfoFormatter
import com.lvxingpai.yunkai.model.{ ContactRequest, SecretKey, UserInfo, _ }
import com.lvxingpai.yunkai.serialization.{ TokenRedisParse, ValidationCodeRedisFormat, ValidationCodeRedisParse }
import com.lvxingpai.yunkai.service.{ RedisFactory, ViaeGateway }
import com.lvxingpai.yunkai.utils.RequestUtils
import com.mongodb.{ DuplicateKeyException, MongoCommandException }
import com.twitter.util.Try.PredicateDoesNotObtain
import com.twitter.util.{ Base64StringEncoder, Future, FuturePool }
import com.typesafe.config.ConfigException
import org.apache.commons.io.IOUtils
import org.bson.types.ObjectId
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.UpdateOperations

import scala.collection.JavaConversions._
import scala.language.{ implicitConversions, postfixOps }
import scala.util.Random

/**
 * 用户账户管理。包括但不限于：
 *
 * * 用户注册
 * * 用户登录
 * * 用户好友管理
 *
 */
class AccountManager @Inject() (@Named("yunkai") ds: Datastore, implicit val futurePool: FuturePool) {

  lazy val groupManager = Global.injector.getInstance(classOf[GroupManager])

  /**
   * 获得用户信息
   *
   * @return 用户信息
   */
  def getUserById(userId: Long, fields: Seq[UserInfoProp] = Seq(), selfId: Option[Long]): Future[Option[yunkai.UserInfo]] = {
    for {
      userMap <- getUsersByIdList(fields, selfId, userId)
    } yield userMap.toSeq.head._2
  }

  /**
   * 将UserInfoProp转换为字段名称
   *
   * @param prop
   * @return
   */
  implicit def userInfoPropToFieldName(prop: UserInfoProp): String = {
    prop match {
      case UserInfoProp.Id => UserInfo.fdId
      case UserInfoProp.UserId => UserInfo.fdUserId
      case UserInfoProp.NickName => UserInfo.fdNickName
      case UserInfoProp.Signature => UserInfo.fdSignature
      case UserInfoProp.Avatar => UserInfo.fdAvatar
      case UserInfoProp.Tel => UserInfo.fdTel
      case UserInfoProp.Gender => UserInfo.fdGender
      case UserInfoProp.Roles => UserInfo.fdRoles
      case UserInfoProp.Residence => UserInfo.fdResidence
      case UserInfoProp.Birthday => UserInfo.fdBirthday
      case UserInfoProp.PromotionCode => UserInfo.fdPromotionCode
      case _ => throw new IllegalArgumentException("Illegal arguemnt: %s" format prop.toString)
    }
  }

  /**
   * 更新用户信息
   *
   * @param userId    需要更新的用户Id
   * @param userInfo  需要更新的用户信息
   * @return
   */
  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, Any]): Future[UserInfo] = futurePool {
    import UserInfoProp._

    // 将用户输入的userInfo规范化
    def processUserInfo(prop: UserInfoProp, value: Any) = {
      prop match {
        case item if Seq(NickName, Signature, Residence) contains item => value.toString.trim
        case item if item == Gender =>
          val gender = value.toString.toLowerCase
          if (Seq("m", "f", "s", "b") contains gender)
            gender
          else
            throw new InvalidArgsException(Some(s"Invalid gender $gender"))
        case item if item == Avatar =>
          val avatar = value.toString
          if (avatar startsWith "http://")
            avatar
          else
            throw new InvalidArgsException(Some(s"Invalid avatar $avatar"))
        case item if item == Birthday =>
          val birthday = value.toString.trim
          val pattern = """(\d{2})/(\d{2})/(\d{4})""".r
          val m = pattern.findFirstMatchIn(birthday)
          if (m isEmpty)
            throw new InvalidArgsException(Some(s"Invalid date of birth: $birthday"))
          else
            birthday
        case _ =>
          None
      }
    }

    // 处理输入的用户信息
    // 只允许更新一小部分字段信息
    val filteredUserInfo = Map(userInfo.toSeq map (v => v._1 -> processUserInfo(v._1, v._2)) filter (_ != None): _*)

    // 获得需要处理的字段名
    val fieldNames = ((filteredUserInfo.keys.toSeq ++ Seq(UserInfoProp.UserId, UserInfoProp.Id))
      map userInfoPropToFieldName)

    if (filteredUserInfo nonEmpty) {
      val query = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(userId)
        .retrievedFields(true, fieldNames: _*)
      val updateOps = filteredUserInfo.foldLeft(ds.createUpdateOperations(classOf[UserInfo]))((ops, entry) => {
        val (key, value) = entry
        ops.set(key, value)
      })

      val result = ds.findAndModify(query, updateOps)
      if (result == null)
        throw NotFoundException(Some(s"Cannot find user: $userId"))
      else {
        // 触发修改个人信息事件
        val updateInfo = new ObjectMapper().createObjectNode()
        updateInfo.put("nickName", "new nickname")
        updateInfo.put("avatar", "new avatar")
        updateInfo.put("signature", "new signature")

        val eventArgs: Map[String, JsonNode] = Map(
          "user" -> userConversion(result),
          "updateInfo" -> updateInfo
        )
        EventEmitter.emitEvent(EventEmitter.evtModUserInfo, eventArgs)
        // 返回userInfo
        result
      }
    } else
      throw new InvalidArgsException(Some("Invalid updated fields"))
  }

  /**
   * 更改用户的身份列表
   *
   * @return
   */
  def updateUserRoles(userId: Long, addRoles: Boolean, roles: Seq[Role]): Future[UserInfo] = {
    import UserInfo._

    val cls = classOf[UserInfo]
    val query = ds.createQuery(cls) field fdUserId equal userId retrievedFields (true, fdRoles)

    futurePool {
      val rolesJ = seqAsJavaList(roles map (_.value))
      val result = if (roles != null && roles.nonEmpty) {
        val ops = if (addRoles)
          ds.createUpdateOperations(cls).addAll(fdRoles, rolesJ, false)
        else
          ds.createUpdateOperations(cls).removeAll(fdRoles, rolesJ)
        ds.findAndModify(query, ops, false)
      } else
        query.get()

      if (result == null)
        throw NotFoundException(Some(s"Cannot find user: $userId"))
      else
        result
    }
  }

  /**
   * 判断两个用户是否为好友
   *
   * @return
   */
  def isContact(userA: Long, userB: Long): Future[Boolean] = {
    val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)

    //    val userList = getUsersByIdList(Seq(), user1, user2)
    val relationship = futurePool {
      val rel = ds.createQuery(classOf[Relationship]).field(Relationship.fdUserA).equal(user1)
        .field(Relationship.fdUserB).equal(user2).field(Relationship.fdContactA).equal(true)
        .field(Relationship.fdContactB).equal(true)
        .retrievedFields(true, Relationship.fdId)
        .get
      rel != null
    }

    for {
      //      l <- userList
      rel <- relationship
    } yield {
      //      if (l exists (_._2 isEmpty))
      //        throw NotFoundException()
      //      else
      rel
    }
  }

  /**
   * 添加好友
   *
   * @param userId      需要被添加好友的用户的ID
   * @param targetUsers 需要被添加的好友的ID
   * @return
   */
  def addContact(userId: Long, targetUsers: Long*): Future[Unit] = {
    val targetUsersFiltered = (targetUsers filter (_ != userId)).toSet.toSeq
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)

    getUsersByIdList(responseFields, None, userId +: targetUsersFiltered: _*) flatMap (m => {
      // 相应的用户必须存在
      if (m exists (_._2 isEmpty))
        throw NotFoundException()
      val cls = classOf[Relationship]

      val jobs = targetUsersFiltered map (target => futurePool {
        val (user1, user2) = if (userId <= target) (userId, target) else (target, userId)
        val op = ds.createUpdateOperations(cls).set(Relationship.fdUserA, user1).set(Relationship.fdUserB, user2)
          .set(Relationship.fdContactA, true).set(Relationship.fdContactB, true)
        val query = ds.createQuery(cls).field(Relationship.fdUserA).equal(user1)
          .field(Relationship.fdUserB).equal(user2)
        ds.updateFirst(query, op, true)

        // 触发添加联系人的事件
        val sender = m(userId).get
        val receiver = m(target).get

        val eventArgs: Map[String, JsonNode] = Map(
          "user" -> sender,
          "target" -> receiver
        )
        EventEmitter.emitEvent(EventEmitter.evtAddContact, eventArgs)
      })

      Future.collect(jobs) map (_ => ())
    })
  }

  /**
   * 删除好友
   *
   * @param userId      需要被删除好友的用户的ID
   * @param targetUsers 需要被删除的好友的ID
   *
   * @return
   */
  def removeContacts(userId: Long, targetUsers: Long*): Future[Unit] = {
    val targetUsersFiltered = (targetUsers filter (_ != userId)).toSet.toSeq
    getUsersByIdList(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar), None, userId +: targetUsersFiltered: _*) map (m => {
      // 相应的用户必须存在
      if (m exists (_._2 isEmpty))
        throw NotFoundException()
      else {
        val cls = classOf[Relationship]
        targetUsersFiltered map (target => futurePool {
          val op = if (userId <= target)
            ds.createUpdateOperations(cls).set(Relationship.fdContactB, false)
          else
            ds.createUpdateOperations(cls).set(Relationship.fdContactA, false)
          val (userA, userB) = if (userId <= target) (userId, target) else (target, userId)
          val query = ds.createQuery(cls).field(Relationship.fdUserA).equal(userA)
            .field(Relationship.fdUserB).equal(userB)
          ds.updateFirst(query, op)

          // 触发删除联系人的事件
          val userAInfo = m(userId).get
          for (elem <- targetUsersFiltered) {
            val userBInfo = m(elem).get
            val eventArgs: Map[String, JsonNode] = Map(
              "user" -> userAInfo,
              "target" -> userBInfo
            )
            EventEmitter.emitEvent(EventEmitter.evtRemoveContact, eventArgs)
          }

        })
      }
    })
  }

  /**
   * 获得用户的好友列表
   *
   * @param userId  用户ID
   * @param include 是包含字段还是排除字段
   * @param fields  需要返回那些字段
   * @param offset  翻页机制
   * @param count   翻页机制
   *
   * @return
   */
  def getContactList(userId: Long, include: Boolean = true, fields: Seq[UserInfoProp] = Seq(), offset: Option[Int] = None,
    count: Option[Int] = None): Future[Seq[yunkai.UserInfo]] = {
    val criteria = Seq(Relationship.fdUserA, Relationship.fdUserB) map (f => ds.createQuery(classOf[Relationship]).criteria(f).equal(userId))
    val queryRel = ds.createQuery(classOf[Relationship]).field(Relationship.fdContactA).equal(true).field(Relationship.fdContactB).equal(true)
    queryRel.or(criteria: _*)
    val defaultOffset = 0
    val defaultCount = 1000

    // 获得好友的userId
    val contactIds = futurePool {
      queryRel.offset(offset.getOrElse(defaultOffset)).limit(count.getOrElse(defaultCount))
      val result = for {
        rel <- queryRel.asList().toSeq
        userIds <- Seq(rel.getUserA, rel.getUserB)
      } yield userIds

      result.toSet.toSeq filter (_ != userId)
    }
    for {
      ids <- contactIds
      contactsMap <- getUsersByIdList(fields, Option(userId), ids: _*)
    } yield {
      println("contactIds = " + ids)
      val result = contactsMap.toSeq.map(_._2.orNull) filter (_ != null)
      for (ele <- result)
        println(ele.nickName)
      result
    }
  }

  /**
   * 修改用户备注
   * @param userA 修改人
   * @param userB 被修改人
   * @param memo  备注
   * @return
   */
  def updateMemo(userA: Long, userB: Long, memo: String): Future[Unit] = {
    val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
    val query = ds.createQuery(classOf[Relationship]).field(Relationship.fdUserA).equal(user1).field(Relationship.fdUserB).equal(user2)
    val updateOps = if (userA <= userB)
      ds.createUpdateOperations(classOf[Relationship]).set(Relationship.fdMemoB, memo)
    else ds.createUpdateOperations(classOf[Relationship]).set(Relationship.fdMemoA, memo)
    futurePool {
      ds.updateFirst(query, updateOps)
      // 触发修改备注事件
      val updateInfo = new ObjectMapper().createObjectNode()
      updateInfo.put("memoB", memo)
      val eventArgs: Map[String, JsonNode] = Map(
        "updateInfo" -> updateInfo
      )
      //      EventEmitter.emitEvent(EventEmitter.evtModUserInfo, eventArgs)
    }
  }

  /**
   * 给定一个ContactRequest，生成相应的MongoDB update operation
   * @param req
   * @return
   */
  private def buildContactRequestUpdateOps(req: ContactRequest): UpdateOperations[ContactRequest] = {
    import ContactRequest._
    val updateOps = ds.createUpdateOperations(classOf[ContactRequest])
      .set(fdSender, req.sender)
      .set(fdReceiver, req.receiver)
      .set(fdTimestamp, req.timestamp)
      .set(fdExpire, req.expire)
      .set(fdStatus, req.status)
    if (req.requestMessage != null)
      updateOps.set(fdRequestMessage, req.requestMessage)
    if (req.rejectMessage != null)
      updateOps.set(fdRejectMessage, req.rejectMessage)
    updateOps
  }

  /**
   * 当产生逐渐冲突时，MongoDB会抛出MongoCommandException异常，在其中的detailedMessage部分。
   * 该函数判断一个MongoCommandException异常是否为这种情况。
   * @return
   */
  private def isDuplicateKeyException(ex: MongoCommandException): Boolean = ex.getErrorMessage contains "duplicate key"

  /**
   * 获得某个接收者（注意，不是请求发送者）名下所有的好友列表，按照时间逆序排列
   * @return
   */
  def getContactRequestList(userId: Long, offset: Int, limit: Int): Future[Seq[ContactRequest]] = {
    for {
      userInfoOpt <- getUserById(userId, Seq(), None)
    } yield {
      if (userInfoOpt isEmpty)
        throw NotFoundException()
      else {
        ds.createQuery(classOf[ContactRequest]).field(ContactRequest.fdReceiver).equal(userId)
          .order(s"-${ContactRequest.fdTimestamp}")
          .offset(offset).limit(limit).asList().toSeq
      }
    }
  }

  /**
   * 发送好友请求
   * @param sender    请求发送者
   * @param receiver  请求接收者
   * @param message   请求附言
   * @return
   */
  def sendContactRequest(sender: Long, receiver: Long, message: Option[String] = None): Future[ObjectId] = {
    // 检查用户是否存在
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    for {
      users <- getUsersByIdList(responseFields, None, sender, receiver)
      relationship <- isContact(sender, receiver)
    } yield {
      if (relationship)
        throw InvalidStateException(Some(s"$sender and $receiver are already contacts"))
      else if (users exists (_._2.isEmpty))
        throw NotFoundException()
      else {
        import ContactRequest._

        val req = ContactRequest(sender, receiver, message, None)

        // 只有在下列情况下，可以发送好友申请
        // * 从未发送过好友申请
        // * 发送过好友申请，且前一申请的状态CANCELLED
        // * 发送过好友申请，且前一申请的状态为PENDING，且已经过期
        // UPDATED: 我决定放开这个限制，可以无限制地发送请求，只不过requestId不会改变

        val cls = classOf[ContactRequest]
        val query = ds.createQuery(cls).field(fdSender).equal(sender).field(fdReceiver).equal(receiver)

        // val criteria1 = ds.createQuery(cls).criteria(fdStatus).equal(CANCELLED.id)
        //        val criteria2 = ds.createQuery(cls).criteria(fdStatus).equal(PENDING.id)
        // val criteria3 = ds.createQuery(cls).criteria(fdExpire).lessThan(currentTime)

        // query.and(ds.createQuery(cls).or(
        // criteria1,
        // criteria3))
        //          ds.createQuery(cls).and(criteria2, criteria3)))

        val updateOps = buildContactRequestUpdateOps(req).unset(fdRejectMessage) //.set(fdContactRequestId, UUID.randomUUID().toString)
        val newRequest = try {
          ds.findAndModify(query, updateOps, false, true)
        } catch {
          // 如果发生该异常，说明系统中已经存在一个好友请求，且不允许重复发送请求
          // 比如：前一个请求处于PENDING状态，且未过期，或者前一个请求已经被拒绝等）
          case ex: MongoCommandException =>
            if (isDuplicateKeyException(ex))
              throw InvalidStateException()
            else
              throw ex
        }
        // 触发发送好友请求
        val senderInfo = users(sender).get
        val receiverInfo = users(receiver).get
        sendContactRequestEvents(
          "viae.event.social.onSendContactRequest",
          newRequest.id, message, senderInfo, receiverInfo
        )
        newRequest.id
      }
    }
  }

  /**
   * 生成contactRequest相关事件的eventArgs
   * @param requestId
   * @param message
   * @param sender
   * @param receiver
   */
  private def sendContactRequestEvents(eventName: String, requestId: ObjectId, message: Option[String],
    sender: UserInfo, receiver: UserInfo) {
    val formatter = Global.injector getInstance classOf[UserInfoFormatter]
    val viae = Global.injector getInstance classOf[ViaeGateway]

    val senderNode = formatter.formatJsonNode(Implicits.YunkaiConversions.userConversion(sender))
    val receiverNode = formatter.formatJsonNode(Implicits.YunkaiConversions.userConversion(receiver))
    viae.sendTask(
      eventName,
      kwargs = Some(Map(
        "sender" -> senderNode,
        "receiver" -> receiverNode,
        "request_id" -> requestId.toString,
        "message" -> (message getOrElse "")
      ))
    )
  }

  /**
   * 拒绝一个好友请求
   * @param requestId 请求ID
   * @param message   拒绝请求的附言
   * @return
   */
  def rejectContactRequest(requestId: String, message: Option[String] = None): Future[Unit] = {
    import ContactRequest.RequestStatus._
    import ContactRequest._

    getContactRequest(requestId) map (oldRequest => {
      if (oldRequest isEmpty)
        throw NotFoundException(Some(s"Cannot find the request $requestId"))
      else {
        val cls = classOf[ContactRequest]
        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
        val updateOps = ds.createUpdateOperations(cls).set(fdStatus, REJECTED.id)
        if (message nonEmpty)
          updateOps.set(fdRejectMessage, message.get)

        val newRequest = ds.findAndModify(query, updateOps, false, false)
        if (newRequest == null)
          throw InvalidStateException()

        // 触发拒绝好友请求
        val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
        val senderId = oldRequest.get.sender
        val receiverId = oldRequest.get.receiver
        val users = getUsersByIdList(responseFields, None, senderId, receiverId)
        for {
          userInfos <- users
        } yield {
          sendContactRequestEvents("viae.event.social.onRejectContactRequest", newRequest.id, message,
            userInfos(receiverId).get, userInfos(senderId).get)
        }
      }
    })
  }

  /**
   * 接受一个好友请求
   * @param requestId 请求ID
   * @return
   */
  def acceptContactRequest(requestId: String): Future[Unit] = {
    import ContactRequest.RequestStatus._
    import ContactRequest._

    getContactRequest(requestId = requestId) flatMap (oldRequest => {
      if (oldRequest isEmpty)
        throw NotFoundException(Some(s"Cannot find the request $requestId"))
      else {
        val cls = classOf[ContactRequest]

        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
        val updateOps = ds.createUpdateOperations(cls).set(fdStatus, ACCEPTED.id)

        val newRequest = ds.findAndModify(query, updateOps, false, false)
        if (newRequest == null)
          throw InvalidStateException()

        addContact(newRequest.sender, newRequest.receiver)
        // 触发接受好友请求
        val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
        val senderId = oldRequest.get.sender
        val receiverId = oldRequest.get.receiver
        val users = getUsersByIdList(responseFields, None, senderId, receiverId)
        for {
          userInfos <- users
        } yield {
          sendContactRequestEvents("viae.event.social.onAcceptContactRequest", newRequest.id, None,
            userInfos(receiverId).get, userInfos(senderId).get)
        }
      }
    })
  }

  /**
   * 取消一个好友请求
   * @param requestId 请求ID
   * @return
   */
  def cancelContactRequest(requestId: String): Future[Unit] = {
    import ContactRequest.RequestStatus._
    import ContactRequest._

    getContactRequest(requestId = requestId) map (oldRequest => {
      if (oldRequest isEmpty)
        throw NotFoundException(Some(s"Cannot find the request $requestId"))
      else {
        val cls = classOf[ContactRequest]

        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
          .field(fdStatus).equal(PENDING.id)
        val updateOps = ds.createUpdateOperations(cls).set(fdStatus, CANCELLED.id)
        ds.updateFirst(query, updateOps)
      }
    })
  }

  /**
   * 获得一个好友请求的详情
   * @param sender    请求发送者
   * @param receiver  请求接收者
   * @return
   */
  def getContactRequest(sender: Long, receiver: Long): Future[Option[ContactRequest]] = futurePool {
    import ContactRequest._
    val req = ds.createQuery(classOf[ContactRequest]).field(fdSender).equal(sender).field(fdReceiver).equal(receiver).get()
    if (req == null)
      throw NotFoundException(Some("Cannot find the request"))
    else
      Option(req)
  }

  /**
   * 获得好友请求的详情
   * @param requestId
   * @return
   */
  def getContactRequest(requestId: String): Future[Option[ContactRequest]] =
    futurePool {
      Option(ds.createQuery(classOf[ContactRequest]).field(ContactRequest.fdContactRequestId)
        .equal(new ObjectId(requestId)).get())
    }

  /**
   * 获得用户的好友个数
   *
   * @param userId    用户ID
   * @return
   */
  def getContactCount(userId: Long): Future[Int] = {
    val userFuture = getUserById(userId, Seq(), None)

    userFuture map (userInfo => {
      if (userInfo isEmpty)
        throw NotFoundException(Some(s"User not find $userId"))
      else {
        val criteria = Seq(Relationship.fdUserA, Relationship.fdUserB) map
          (f => ds.createQuery(classOf[Relationship]).criteria(f).equal(userId))
        val query = ds.createQuery(classOf[Relationship]).field(Relationship.fdContactA).equal(true).field(Relationship.fdContactB).equal(true)
        query.or(criteria: _*)
        query.countAll().toInt
      }
    })
  }

  /**
   *
   * 如果user的tel字段为UUID（占位符情况），说明用户没有tel信息，将其置为null
   * @param user
   * @return
   */
  def filterUUIDTel(user: UserInfo): UserInfo = {
    if (user.tel != null && user.tel.length == 36)
      user.tel = null
    user
  }

  /**
   * 批量获得多个用户的信息
   *
   * @param userIds 需要查找的用户的ID
   * @return
   */
  def getUsersByIdList(fields: Seq[UserInfoProp], selfId: Option[Long], userIds: Long*): Future[Map[Long, Option[yunkai.UserInfo]]] = {
    import UserInfoProp._

    futurePool {
      if (userIds isEmpty) {
        Map[Long, Option[yunkai.UserInfo]]()
      } else {
        val query = userIds length match {
          case 1 => ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(userIds head)
          case _ => ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(seqAsJavaList(userIds))
        }
        // 获得需要处理的字段名
        val allowedProperties = Seq(UserId, NickName, Avatar, Signature, Gender, Tel, Roles, Birthday, Residence, PromotionCode)
        val retrievedFields = (fields filter (allowedProperties.contains(_))) ++ Seq(UserId, Id) map userInfoPropToFieldName

        query.retrievedFields(true, retrievedFields: _*)
        val results = Map(query.asList() map filterUUIDTel map (item => item.userId -> item): _*) map {
          case (userId, user) =>
            // TODO 处理avatar为{url: 形式}
            val result = Option(user.avatar) flatMap (avatar => {
              if (avatar.isEmpty) {
                None
              } else if (avatar startsWith "http") {
                Some(avatar)
              } else {
                val mapper = new ObjectMapper()
                Option(mapper.readTree(avatar) get "url") map (_.asText())
              }
            })
            user.avatar = result.orNull
            userId -> user
        }
        if (selfId.isEmpty) {
          Map(userIds map (v => v -> (results get v map userConversion)): _*)
        } else {
          val userInfos = results map { user =>
            {
              // 设置备注
              val (userId1, userId2, retrievedField) = if (selfId.get <= user._1) (selfId.get, user._1, Relationship.fdMemoB) else (user._1, selfId.get, Relationship.fdMemoA)
              val query = ds.createQuery(classOf[Relationship]).field("userA").equal(userId1).field("userB").equal(userId2)
                .retrievedFields(true, retrievedField)
              val rel = query.get
              val memo = if (rel == null) ""
              else {
                if (selfId.get <= user._1) rel.memoB else rel.memoA
              }
              user._2.memo = memo
              user
            }
          }
          Map(userIds map (v => v -> (userInfos get v map userConversion)): _*)
        }
      }
    }
  }

  /**
   * 验证用户密码是否正确
   *
   * @param userId    用户名
   * @param password  密码
   * @return
   */
  def verifyCredential(userId: Long, password: String): Future[Boolean] = {
    val query = ds.createQuery(classOf[Credential]).field(Credential.fdUserId).equal(userId)
    futurePool {
      val credential = query.get()
      credential != null && {
        // 对于第三方OAuth账号登录的用户，可能没有设置密码。此时，返回true
        if (credential.salt.isEmpty || credential.passwdHash.isEmpty)
          true
        else {
          val crypted = saltPassword(password, Some(credential.salt))._2
          crypted == credential.passwdHash
        }
      }
    }
  }

  /**
   * 重置某个userId对应的secretKey
   * @param userId
   * @param expire
   * @return
   */
  def resetSecretKey(userId: Long, expire: Option[Date] = None): Future[SecretKey] = {
    val newKey = new SecretKey()
    newKey.key = Base64StringEncoder.encode(MessageDigest.getInstance("SHA-1")
      .digest(UUID.randomUUID().toString.getBytes))
    newKey.timestamp = new Date()
    newKey.expire = expire.orNull

    futurePool {
      val query = ds.createQuery(classOf[Credential]).field("userId").equal(userId)
      val ops = ds.createUpdateOperations(classOf[Credential]).set("secretKey", newKey)
      val result = ds.update(query, ops, true)
      if (result.getUpdatedCount != 1 && result.getInsertedCount != 1) {
        // 没有找到userId对应的记录
        throw NotFoundException()
      }
      newKey
    }
  }

  /**
   * 用户登录
   *
   * @param loginName   登录用户名（默认情况下是注册的手机号）
   * @param password    登录密码
   * @return
   */
  def login(loginName: String, password: String, source: String): Future[yunkai.UserInfo] = {
    // 获得用户信息
    val result = for {
      userInfo <- futurePool {
        val retrievedFields = Seq(UserInfo.fdId, UserInfo.fdUserId, UserInfo.fdNickName, UserInfo.fdGender, UserInfo.fdAvatar,
          UserInfo.fdSignature, UserInfo.fdTel, UserInfo.fdLoginStatus, UserInfo.fdLoginSource, UserInfo.fdLoginTime)
        val query = ds.createQuery(classOf[UserInfo]).retrievedFields(true, retrievedFields: _*)
        val telCriteria = query criteria UserInfo.fdTel equal loginName
        val emailCriteria = query criteria UserInfo.fdEmail equal loginName
        query.or(telCriteria, emailCriteria)

        val updateOps = ds.createUpdateOperations(classOf[UserInfo])
          .set(UserInfo.fdLoginStatus, true)
          .set(UserInfo.fdLoginTime, java.lang.System.currentTimeMillis())
          .add(UserInfo.fdLoginSource, source, false)
        ds.findAndModify(query, updateOps, false)
      }
      verified <- Option(userInfo) map (value => verifyCredential(value.userId, password)) getOrElse Future(false) if verified
      secretKeyOpt <- getSecretKey(userInfo.userId) // 获得secret key
      secretKey <- secretKeyOpt map (u => Future(u)) getOrElse resetSecretKey(userInfo.userId)
      newUserInfo <- getUserById(
        userInfo.userId,
        Seq(UserInfoProp.Id, UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Gender, UserInfoProp.Avatar,
          UserInfoProp.Signature, UserInfoProp.Tel, UserInfoProp.LoginStatus, UserInfoProp.LoginSource,
          UserInfoProp.LoginTime), Some(userInfo.userId)
      )
    } yield {
      val viae = Global.injector getInstance classOf[ViaeGateway]
      viae.sendTask(
        "viae.event.account.onLogin",
        kwargs = Some(Map("user_id" -> newUserInfo.get.userId, "source" -> source))
      )
      newUserInfo.get.copy(secretKey = Some(secretKey))
    }
    result rescue {
      case e: PredicateDoesNotObtain =>
        // 发生这个异常, 说明verified的值为false
        throw AuthException()

    }
  }

  /**
   * 根据UserId获得secret key
   * @param userId
   * @return
   */
  def getSecretKey(userId: Long): Future[Option[SecretKey]] = {
    val query = ds.createQuery(classOf[Credential]).field("userId").equal(userId).retrievedFields(true, "secretKey")
    futurePool {
      val credential = query.get()
      Option(credential) flatMap (value => Option(value.secretKey))
    }
  }

  /**
   * 生成邀请码
   * @param length
   * @return
   */
  private def createPromotionCode(length: Int = 4): String = {
    // 生成0-9A-Z之间的随机字符
    def func(): Char = {
      val r = Random.nextInt(36)
      val asciiCode = if (r < 10) r + 48 else r - 10 + 65
      asciiCode.toChar
    }

    0 to (length - 1) map (_ => func()) mkString ""
  }

  // logout: 释放资源, 修改UserInfo的logoutTime和loginSource字段(删除本次登录的来源)
  def createUserPoly(password: String, miscInfo: Option[collection.Map[UserInfoProp, String]]): Future[UserInfo] = {
    // 取得用户ID
    val futureUserId = Global.injector.getInstance(classOf[IdGen.FinagledClient]).generate("user")

    // 创建用户
    val userInfoFuture = for {
      userId <- futureUserId
    } yield {
      val miscInfoMap = miscInfo getOrElse collection.Map()
      val nickName = miscInfoMap.getOrElse(UserInfoProp.NickName, s"旅行派用户$userId")
      val newUser = UserInfo(userId, nickName)

      miscInfoMap get UserInfoProp.Email foreach (newUser.email = _)
      miscInfoMap get UserInfoProp.Tel foreach (newUser.tel = _)

      // 邀请码
      newUser.promotionCode = createPromotionCode()

      newUser
    }

    val (salt, crypted) = saltPassword(password)
    val secretKey = SecretKey()

    // 创建并保存新用户Credential实例
    val credentialFuture = for {
      userId <- futureUserId
    } yield {
      Credential(userId, salt, crypted, secretKey)
    }

    // 是否成功添加了user
    var successUser = false
    (for {
      userInfo <- userInfoFuture
      credential <- credentialFuture
    } yield {
      userInfo.secretKey = secretKey
      ds.save[UserInfo](userInfo)
      successUser = true

      ds.save[Credential](credential)

      // 发布事件
      val viae = Global.injector getInstance classOf[ViaeGateway]
      viae.sendTask(
        "viae.event.account.onCreateUser",
        kwargs = Some(Map("user_id" -> userInfo.userId))
      )
      userInfo
    }) rescue {
      case _: DuplicateKeyException =>
        // 如果成功添加了user, 但是添加credential出错:
        if (successUser) userInfoFuture map ds.delete[UserInfo]
        throw InvalidArgsException(Some(s"Duplicated resources"))
      case e: Throwable =>
        if (successUser) userInfoFuture map ds.delete[UserInfo]
        throw e
    }
  }

  /**
   * Check the validation code. If passed, a token string, which is essentially the validation code's fingerprint
   * will be returned. Otherwise, None will be returned.
   *
   * @return
   */
  def checkValidationCode(valCode: String, action: OperationCode, tel: String, countryCode: Option[Int] = None): Future[Option[String]] = {
    val redisKey = ValidationCode.calcRedisKey(action, tel, countryCode)
    val magicCode = try {
      Global.conf.getString("smscenter.magicCode")
    } catch {
      case _: ConfigException.Missing => ""
    }

    futurePool {
      RedisFactory.pool.withClient(client => {
        implicit val parse = ValidationCodeRedisParse()
        implicit val parse2 = TokenRedisParse()
        implicit val format = ValidationCodeRedisFormat()

        (client.get[ValidationCode](redisKey) map (code => {
          // The validation will be passed if and only if:
          // * The code coincides
          // * The action conincides
          val checkResult = (magicCode.nonEmpty && magicCode == valCode) ||
            (code.code == valCode && code.action == action) // && !code.checked)

          // Set the validation code to CHECKED status no matter the check result is true or not.
          val expire = 10 * 60 * 1000L // 10分钟后过期

          // Generate a token
          if (checkResult) {
            val tokenKey = "yunkai:token/%s" format UUID.randomUUID().toString
            val token = Token(tokenKey, action, code.userId, countryCode, Some(code.tel), System.currentTimeMillis)
            client.setex(tokenKey, expire / 1000, token)
            client.del(redisKey)
            Some(tokenKey)
          } else {
            //            code.checked = true
            //            client.setex(redisKey, expire / 1000, code)
            None
          }
        })) getOrElse None
      })
    }
  }

  /**
   * 取回一个token。注意，每个token只能被access一次。也就是说，此操作会删除对应的token。
   * @return
   */
  def fetchToken(token: String): Future[Option[Token]] = {
    futurePool {
      RedisFactory.pool.withClient(client => {
        implicit val parse = TokenRedisParse()
        val result = client.get[Token](token)
        //client.del(token)
        result
      })
    }
  }

  def sendValidationCode(action: OperationCode, userId: Option[Long], tel: String, countryCode: Option[Int] = None): Future[Unit] = {
    val resendInterval = 60 * 1000L // 1分钟的发送间隔
    val digits = f"${Random.nextInt(1000000)}%06d"
    val redisKey = ValidationCode.calcRedisKey(action, tel, countryCode)

    import OperationCode.{ ResetPassword, Signup, UpdateTel }
    implicit val format = ValidationCodeRedisFormat()
    implicit val parse = ValidationCodeRedisParse()

    // 发送短信
    def sendSms(): Future[Unit] = {
      val message = action match {
        case item if item.value == Signup.value =>
          s"为手机%s注册旅行派账户。验证码：$digits" format tel
        case item if item.value == ResetPassword.value =>
          s"正在重置密码。验证码：$digits"
        case item if item.value == UpdateTel.value =>
          s"正在绑定手机。验证码：$digits"
      }

      Global.injector.getInstance(classOf[SmsCenter.FinagledClient]).sendSms(message, Seq(tel)) map (s => ())
    }

    // 是否超过发送限额
    val quotaExceeds = futurePool {
      RedisFactory.pool withClient (_.get[ValidationCode](redisKey) map
        (_.createTime + resendInterval < System.currentTimeMillis) getOrElse true)
    }

    // 通过手机号查找用户。返回值（true, user)。前者表示检验结果，后者表示对应的用户信息
    val telSearch: Future[Option[yunkai.UserInfo]] = {
      val result = searchUserInfo(Map(UserInfoProp.Tel -> tel), None, None, None)
      action match {
        case item if item.value == Signup.value =>
          result map (_.headOption)
        case item if item.value == ResetPassword.value || item.value == UpdateTel.value =>
          result map (v => if (v.length == 1) v.headOption else None)
        case _ => throw InvalidArgsException(Some("Invalid operation code"))
      }
    }

    val userIdFuture = if (userId nonEmpty) getUserById(userId.get, Seq(), None)
    else futurePool {
      None
    }

    // 当且仅当上述两个条件达成的时候，才生成验证码并发送
    for {
      quotaFlag <- quotaExceeds
      telSearchResult <- telSearch
      userId1 <- userIdFuture
      _ <- {
        if (!quotaFlag)
          throw OverQuotaLimitException()

        val expire = 10 * 60 * 1000L // 10分钟后过期
        val code = action match {
          case item if item.value == Signup.value =>
            if (telSearchResult nonEmpty)
              // 手机号码已存在
              throw ResourceConflictException(Some(s"The phone number $tel already exists"))
            else
              ValidationCode(digits, action, None, tel, countryCode)
          case item if item.value == ResetPassword.value =>
            if (telSearchResult isEmpty)
              // 不存在相应的用户
              throw InvalidArgsException(Some(s"The phone number $tel is incorrect"))
            else
              ValidationCode(digits, action, Some(telSearchResult.get.userId), tel, countryCode)
          case item if item.value == UpdateTel.value =>
            if (userId isEmpty)
              throw InvalidArgsException(Some(s"Not provide a userId"))
            else {
              if (userId1 isEmpty) {
                throw InvalidArgsException(Some(s"The userId ${userId.get} is not exist"))
              } else {
                ValidationCode(digits, action, userId, tel, countryCode)
              }
            }
        }

        if (RedisFactory.pool.withClient(_.setex(redisKey, expire / 1000, code)))
          sendSms()
        else
          Future()
      }
    } yield ()
  }

  /**
   * 重置密码
   * @return
   */
  def resetPassword(userId: Long, oldPassword: String, newPassword: String): Future[Unit] = {
    verifyCredential(userId, oldPassword) flatMap (result => {
      if (!result)
        throw AuthException()
      else {
        resetPasswordImpl(userId, newPassword)
      }
    })
  }

  /**
   * 修改用户密码的代码实现
   * @return
   */
  private def resetPasswordImpl(userId: Long, newPassword: String): Future[Unit] = {

    def emitEvent(): Future[Unit] = {
      // 触发重置用户密码的事件
      val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
      val user = getUserById(userId, responseFields, None)
      for {
        elem <- user
      } yield {
        elem foreach (userInfo => {
          val eventArgs: Map[String, JsonNode] = Map(
            "user" -> user2JsonNode(userInfo)
          )
          EventEmitter.emitEvent(EventEmitter.evtResetPassword, eventArgs)
        })
      }
    }

    futurePool {
      if (!checkPassword(newPassword))
        throw InvalidArgsException()

      val query = ds.find(classOf[Credential], Credential.fdUserId, userId)
      val (salt, crypted) = saltPassword(newPassword)
      // 更新Credential
      val updateOps = ds.createUpdateOperations(classOf[Credential]).set(Credential.fdSalt, salt)
        .set(Credential.fdPasswdHash, crypted)
      ds.updateFirst(query, updateOps, true)
      emitEvent()
      ()
    }
  }

  // 验证密码是否合法（必须是ASCII 33~126之间的字符，且长度为6~32）
  private def checkPassword(password: String): Boolean = {
    val len = password.length
    val illegalChar = password exists (c => {
      val ord = c.toInt
      ord < 33 || ord > 126
    })
    len >= 6 && len <= 32 && !illegalChar
  }

  /**
   * 根据token修改用户密码
   *
   * @return
   */
  def resetPasswordByToken(userId: Long, newPassword: String, token: String): Future[Unit] = {
    val result = for {
      checked1 <- verifyToken(OperationCode.ResetPassword, token, userId = Some(userId))
      checked2 <- verifyToken(OperationCode.UpdateTel, token, userId = Some(userId))
    } yield checked1 || checked2

    result flatMap (checked => {
      if (!checked)
        throw AuthException()
      else
        resetPasswordImpl(userId, newPassword)
    })
  }

  /**
   * 验证Token是否有效
   * @return
   */
  private def verifyToken(action: OperationCode, token: String, userId: Option[Long] = None, tel: Option[String] = None): Future[Boolean] = {
    for {
      opt <- fetchToken(token)
    } yield {
      // 验证token是否有效。判断标准
      // * token存在
      // * action一致
      // * userId一致
      // * 未过期
      opt exists (token => {
        (userId.nonEmpty || tel.nonEmpty) &&
          userId.map(_ == token.userId.getOrElse(0)).getOrElse(true) &&
          tel.map(_ == token.tel.getOrElse("")).getOrElse(true)
      })
    }
  }

  /**
   * 更新用户手机号码
   *
   * @param userId
   * @param tel
   * @return
   */
  def updateTelNumber(userId: Long, tel: String, token: String): Future[Unit] = {
    verifyToken(OperationCode.UpdateTel, token, userId = Some(userId)) map (checked => {
      if (!checked)
        throw AuthException()
      else {
        import UserInfo._

        val cls = classOf[UserInfo]
        val query = ds.createQuery(cls).field(fdUserId).equal(userId)
        val updateOps = ds.createUpdateOperations(cls).set(fdTel, tel)
        try {
          val result = ds.updateFirst(query, updateOps)
          if (!result.getUpdatedExisting)
            throw NotFoundException(Some(s"Cannot find user $userId"))
        } catch {
          case _: DuplicateKeyException => throw ResourceConflictException(Some(s"Phone number $tel already exists"))
        }
      }
    })
  }

  /**
   * 返回salt和密文
   * @return
   */
  private def saltPassword(password: String, salt: Option[String] = None): (String, String) = {
    // 生成64个字节的salt
    val theSalt = salt.getOrElse(MessageDigest.getInstance("MD5").digest(Random.nextLong().toString.getBytes).toString)

    val bytes = MessageDigest.getInstance("SHA-256").digest((theSalt + password).getBytes)
    theSalt -> (bytes map ("%02x" format _) mkString)
  }

  /**
   * 根据电话号码和昵称搜索用户。
   *
   * @param queryFields
   * @param fields
   * @param offset
   * @param count
   * @return
   */
  def searchUserInfo(queryFields: Map[UserInfoProp, String], fields: Option[Seq[UserInfoProp]], offset: Option[Int] = None,
    count: Option[Int] = None): Future[Seq[yunkai.UserInfo]] = {
    val cls = classOf[UserInfo]

    val query = ds.createQuery(cls)
    val criteriaList = queryFields.toSeq map (item => {
      item._1 match {
        case UserInfoProp.Tel => ds.createQuery(cls).criteria(UserInfo.fdTel).equal(item._2)
        case UserInfoProp.NickName => ds.createQuery(cls).criteria(UserInfo.fdNickName).startsWith(item._2)
        case _ => null
      }
    }) filter (_ != null)

    if (criteriaList isEmpty)
      futurePool(Seq())
    else {
      query.or(criteriaList: _*)

      // 分页
      val defaultOffset = 0
      val defaultCount = 20

      // 限定查询返回字段
      val retrievedFields = (fields.getOrElse(Seq()) ++ Seq(UserInfoProp.UserId, UserInfoProp.Id)) map {
        case UserInfoProp.UserId => UserInfo.fdUserId
        case UserInfoProp.NickName => UserInfo.fdNickName
        case UserInfoProp.Avatar => UserInfo.fdAvatar
        case UserInfoProp.Tel => UserInfo.fdTel
        case _ => ""
      } filter (_ nonEmpty)

      // 获得符合条件的userId
      futurePool {
        query.offset(offset.getOrElse(defaultOffset)).limit(count.getOrElse(defaultCount))
          .retrievedFields(true, retrievedFields :+ UserInfo.fdUserId: _*)

        query.asList().toSeq.map[yunkai.UserInfo, Seq[yunkai.UserInfo]](v => v)
      }
    }
  }

  def getUserByField(fields: String, value: String): Future[Option[yunkai.UserInfo]] = futurePool {
    Option(ds.createQuery(classOf[UserInfo]).field(fields).hasThisOne(value).get) map userConversion
  }

  def isNumeric(str: String): Boolean = {
    val pattern = Pattern.compile("[0-9]*")
    val isNum = pattern.matcher(str)
    isNum.matches()
  }

  /**
   * 截取userID的后3位，区分重复的昵称
   *
   * @param u
   */
  def nickDuplicateRemoval(u: UserInfo): UserInfo = {
    val uidStr = u.userId.toString
    val size = uidStr.length
    val doc = uidStr.substring(size - 4, size - 1)
    u.nickName = u.nickName + "_" + doc
    u
  }

  def oauthToUserInfo4WX(json: JsonNode): Future[yunkai.UserInfo] = {
    val userInfo = {
      val nickName = json.get("nickname").asText()
      val avatar = json.get("headimgurl").asText()
      val gender = if (json.get("sex").asText().equals("1")) "M" else "F"
      // 取得用户ID
      val futureUserId = Global.injector.getInstance(classOf[IdGen.FinagledClient]).generate("user")
      val provider = "weixin"
      val oauthId = json.get("openid").asText()
      val oauthInfo = OAuthInfo(provider, oauthId, nickName)
      if (isNumeric(nickName)) {
        oauthInfo.nickName = nickName + "_桃子"
      }

      val oauthInfoList = seqAsJavaList(Seq(oauthInfo))
      futureUserId map (userId => {
        val user = UserInfo(userId, nickName)
        user.avatar = avatar
        user.gender = gender
        //user.oauthIdList = seqAsJavaList(Seq(oauthId))
        user.oauthInfoList = oauthInfoList
        //如果第三方昵称已被其他用户使用，则添加后缀
        Option(getUserByField(UserInfo.fdNickName, nickName)) foreach (_ => nickDuplicateRemoval(user))
        user.promotionCode = createPromotionCode()

        // 保存
        ds.save[UserInfo](user)

        val viae = Global.injector getInstance classOf[ViaeGateway]
        viae.sendTask(
          "viae.event.account.onCreateUser",
          kwargs = Some(Map("user_id" -> user.userId))
        )

        user
      }) rescue {
        // 有重复的键
        case _: DuplicateKeyException => throw InvalidArgsException(Some(s"Duplicated resources"))
      }
    }

    userInfo map Implicits.YunkaiConversions.userConversion
  }

  /**
   * 微信登录
   */
  def loginByWeixin(code: String, source: String): Future[yunkai.UserInfo] = {
    val futureInfoNode = futurePool {
      val wxUrl = RequestUtils.getWeiXinUrl(code)
      val acc_url = new URL(wxUrl)
      val acc_json = IOUtils.toString(acc_url, "UTF-8")
      val mapper = new ObjectMapper()
      val rootNode = mapper.readTree(acc_json)
      // 如果请求失败

      // 获取access_token
      val access_token = rootNode.get("access_token").asText()
      val openId = rootNode.get("openid").asText()

      //请求用户信息
      val infoUrl = RequestUtils.getInfoUrl(access_token, openId)
      val info_url = new URL(infoUrl)
      val info_json = IOUtils.toString(info_url, "UTF-8")
      val info_mapper = new ObjectMapper()
      val infoNode = info_mapper.readTree(info_json)
      infoNode
    }
    val futureOauthId = for {
      node <- futureInfoNode
    } yield node.get("openid").asText()

    for {
      oauthId <- futureOauthId
      userOpt <- getUserByField("oauthInfoList.oauthId", oauthId)
      user <- userOpt map (u => Future(u)) getOrElse {
        // 如果第三方用户不存在, 则创建一个
        futureInfoNode flatMap oauthToUserInfo4WX
      }
      secretKeyOpt <- getSecretKey(user.userId)
      secretKey <- secretKeyOpt map (u => Future(u)) getOrElse resetSecretKey(user.userId)
      newUserInfo <- getUserById(
        user.userId,
        Seq(UserInfoProp.Id, UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Gender, UserInfoProp.Avatar,
          UserInfoProp.Signature, UserInfoProp.Tel, UserInfoProp.LoginStatus, UserInfoProp.LoginSource,
          UserInfoProp.LoginTime), Some(user.userId)
      )
    } yield {
      val viae = Global.injector getInstance classOf[ViaeGateway]
      viae.sendTask(
        "viae.event.account.onLogin",
        kwargs = Some(Map("user_id" -> newUserInfo.get.userId, "source" -> source))
      )
      newUserInfo.get.copy(secretKey = Some(secretKey))
    }
  }

  // 黑名单, blockA为true表示userA在userB的黑名单中, blockB为true表示userB在userA的黑名单中
  def isBlocked(selfId: Long, targetId: Long): Future[Boolean] = futurePool {
    import Relationship._

    val query = if (selfId <= targetId)
      ds.createQuery(classOf[Relationship]) field fdUserA equal selfId field fdUserB equal targetId field fdBlockB equal true
    else
      ds.createQuery(classOf[Relationship]) field fdUserB equal selfId field fdUserA equal targetId field fdBlockA equal true

    query.get() != null
  }

  /**
   * 修改用户黑名单属性
   * @param userA 屏蔽人
   * @param userB 被屏蔽人
   * @param block  设置是否屏蔽
   * @return
   */
  def updateBlackList(userA: Long, userB: Long, block: Boolean): Future[Unit] = {
    val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
    val query = ds.createQuery(classOf[Relationship]).field(Relationship.fdUserA).equal(user1).field(Relationship.fdUserB).equal(user2)
    val updateOps = if (userA <= userB)
      ds.createUpdateOperations(classOf[Relationship]).set(Relationship.fdBlockB, block)
    else ds.createUpdateOperations(classOf[Relationship]).set(Relationship.fdBlockA, block)
    futurePool {
      ds.updateFirst(query, updateOps, true)
      // 触发设置黑名单事件
      val updateInfo = new ObjectMapper().createObjectNode()
      updateInfo.put("blockB", block)
      val eventArgs: Map[String, JsonNode] = Map(
        "updateInfo" -> updateInfo
      )
      EventEmitter.emitEvent(EventEmitter.evtAddBlackList, eventArgs)
    }
  }

  def getUsersByTelList(fields: Option[Seq[UserInfoProp]], tels: Seq[String]): Future[Seq[yunkai.UserInfo]] = {
    import UserInfoProp._

    futurePool {
      if (tels isEmpty) {
        Seq[yunkai.UserInfo]()
      } else {
        val query = tels length match {
          case 1 => ds.createQuery(classOf[UserInfo]).field(UserInfo.fdTel).equal(tels head)
          case _ => ds.createQuery(classOf[UserInfo]).field(UserInfo.fdTel).in(seqAsJavaList(tels))
        }
        // 获得需要处理的字段名
        val allowedProperties = Seq(UserId, NickName, Avatar, Signature, Gender, Tel, Roles, Birthday, Residence)
        val retrievedFields = if (fields nonEmpty) {
          (fields.get filter (allowedProperties.contains(_))) ++ Seq(UserId, Id) map userInfoPropToFieldName
        } else Seq()

        query.retrievedFields(true, retrievedFields: _*)
        val results = query.asList().toSeq
        results map (item => userConversion(item))
      }
    }
  }

  def setContact(): Future[Unit] = futurePool {
    val query = ds.createQuery(classOf[Relationship]).field(Relationship.fdUserA).exists()
    val op = ds.createUpdateOperations(classOf[Relationship]).set(Relationship.fdContactA, true).set(Relationship.fdContactB, true)
    ds.update(query, op)
  }
}
