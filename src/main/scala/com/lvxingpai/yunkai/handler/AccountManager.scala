package com.lvxingpai.yunkai.handler

import java.security.MessageDigest

import com.fasterxml.jackson.databind.{ObjectMapper}
import com.fasterxml.jackson.databind.node.{ObjectNode, TextNode}
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.{ContactRequest, Credential, Relationship, UserInfo}
import com.mongodb.{DuplicateKeyException, MongoCommandException}
import com.twitter.util.{Future, FuturePool}
import org.bson.types.ObjectId
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.{CriteriaContainer, UpdateOperations}

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.language.{implicitConversions, postfixOps}
import scala.util.Random

/**
 * 用户账户管理。包括但不限于：
 *
 * * 用户注册
 * * 用户登录
 * * 用户好友管理
 *
 */
object AccountManager {

  /**
   * 获得用户信息
   *
   * @return 用户信息
   */
  def getUserById(userId: Long, fields: Seq[UserInfoProp] = Seq())(implicit ds: Datastore, futurePool: FuturePool): Future[Option[UserInfo]] = {
    for {
      userMap <- getUsersByIdList(fields, userId)
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
      case UserInfoProp.UserId => UserInfo.fdUserId
      case UserInfoProp.NickName => UserInfo.fdNickName
      case UserInfoProp.Signature => UserInfo.fdSignature
      case UserInfoProp.Avatar => UserInfo.fdAvatar
      case UserInfoProp.Tel => UserInfo.fdTel
      case UserInfoProp.Gender => UserInfo.fdGender
      case UserInfoProp.ChatGroups => UserInfo.fdChatGroups
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
  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, Any])(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] = futurePool {
    // 只允许更新一小部分字段信息
    val allowedFields = Seq(UserInfoProp.NickName, UserInfoProp.Signature, UserInfoProp.Gender, UserInfoProp.Avatar)
    val filteredUserInfo = userInfo filter (item => allowedFields contains item._1)

    // The value of a gender should be among ["m", "f", "s", null]
    if (userInfo.contains(UserInfoProp.Gender)) {
      val gender = userInfo(UserInfoProp.Gender)
      if (gender != null && gender != "f" && gender != "m" && gender != "s" && gender != "F" && gender != "M" && gender != "S")
        throw new InvalidArgsException(s"Invalid gender $gender")
    }

    // 获得需要处理的字段名
    val fieldNames = filteredUserInfo.keys.toSeq map userInfoPropToFieldName

    if (filteredUserInfo nonEmpty) {
      val query = ds.find(classOf[UserInfo], "userId", userId).retrievedFields(true, fieldNames: _*)
      val updateOps = filteredUserInfo.foldLeft(ds.createUpdateOperations(classOf[UserInfo]))((ops, entry) => {
        val (key, value) = entry
        if (value != null)
          ops.set(key, value)
        else
          ops.unset(key)
      })

      val result = ds.findAndModify(query, updateOps)
      if (result == null)
        throw NotFoundException(s"Cannot find user: $userId")
      else {
        // 触发修改个人信息事件
        // 修改了哪些字段
        val miscInfo = new ObjectMapper().createObjectNode()
        val user = new ObjectMapper().createObjectNode()
        user.put("id", result.userId)
        user.put("nickName", result.nickName)
        val avatarValue = if (result.avatar != null && result.avatar.nonEmpty) result.avatar else ""
        user.put("avatar", avatarValue)
        val eventArgs = scala.collection.immutable.Map(
          "user" -> user,
          "miscInfo" -> miscInfo
        )
        EventEmitter.emitEvent(EventEmitter.evtModUserInfo, eventArgs)
        // 返回userInfo
        result
      }
    } else
      throw new InvalidArgsException("Invalid updated fields")
  }

  /**
   * 判断两个用户是否为好友
   *
   * @return
   */
  def isContact(userA: Long, userB: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Boolean] =
    futurePool {
      val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
      ds.createQuery(classOf[Relationship]).field(Relationship.fdUserA).equal(user1)
        .field(Relationship.fdUserB).equal(user2).get() != null
    }

  /**
   * 添加好友
   *
   * @param userId      需要被添加好友的用户的ID
   * @param targetUsers 需要被添加的好友的ID
   * @return
   */
  def addContact(userId: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    val targetUsersFiltered = (targetUsers filter (_ != userId)).toSet.toSeq
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)

    getUsersByIdList(responseFields, userId +: targetUsersFiltered: _*) flatMap (m => {
      // 相应的用户必须存在
      if (m exists (_._2 isEmpty))
        throw NotFoundException("")
      val cls = classOf[Relationship]

      val jobs = targetUsersFiltered map (target => futurePool {
        val (user1, user2) = if (userId <= target) (userId, target) else (target, userId)
        val op = ds.createUpdateOperations(cls).set(Relationship.fdUserA, user1).set(Relationship.fdUserB, user2)
        val query = ds.createQuery(cls).field(Relationship.fdUserA).equal(user1)
          .field(Relationship.fdUserB).equal(user2)
        ds.updateFirst(query, op, true)

        // 触发添加联系人的事件
        val sender = m(userId).get
        val receiver = m(target).get
        val miscInfo = new ObjectMapper().createObjectNode()

        val eventArgs = scala.collection.immutable.Map(
          "user" -> user2JsonNode(sender),
          "targets" -> user2JsonNode(receiver),
          "miscInfo" -> miscInfo
        )
        EventEmitter.emitEvent(EventEmitter.evtAddContacts, eventArgs)
      })

      Future.collect(jobs) map (_ => ())
    })
  }
  implicit def user2JsonNode(user: UserInfo): ObjectNode = {
    val targets = new ObjectMapper().createObjectNode()
    targets.put("id", user.userId)
    targets.put("nickName", user.nickName)
    val avatarValue = if (user.avatar != null && user.avatar.nonEmpty) user.avatar else ""
    targets.put("avatar", avatarValue)
    targets
  }

  /**
   * 删除好友
   *
   * @param userId      需要被删除好友的用户的ID
   * @param targetUsers 需要被删除的好友的ID
   *
   * @return
   */
  def removeContacts(userId: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    val targetUsersFiltered = (targetUsers filter (_ != userId)).toSet.toSeq
    getUsersByIdList(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar), userId +: targetUsersFiltered: _*) map (m => {
      // 相应的用户必须存在
      if (m exists (_._2 isEmpty))
        throw NotFoundException("")
      else {
        def buildQuery(user1: Long, user2: Long): CriteriaContainer = {
          val l = Seq(user1, user2).sorted
          ds.createQuery(classOf[Relationship]).criteria(Relationship.fdUserA).equal(l head)
            .criteria(Relationship.fdUserB).equal(l last)
        }

        val query = ds.createQuery(classOf[Relationship])
        query.or(targetUsersFiltered map (buildQuery(userId, _)): _*)
        ds.delete(query)

        // 触发删除联系人的事件
        val userAInfo = m(userId).get
        val miscInfo = new ObjectMapper().createObjectNode()
        for(elem <- targetUsersFiltered) {
          val userBInfo = m(elem).get
          val eventArgs = scala.collection.immutable.Map(
            "user" -> user2JsonNode(userAInfo),
            "targets" -> user2JsonNode(userBInfo),
            "miscInfo" -> miscInfo
          )
          EventEmitter.emitEvent(EventEmitter.evtRemoveContacts, eventArgs)
        }
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
                     count: Option[Int] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[UserInfo]] = {
    val criteria = Seq(Relationship.fdUserA, Relationship.fdUserB) map
      (f => ds.createQuery(classOf[Relationship]).criteria(f).equal(userId))
    val queryRel = ds.createQuery(classOf[Relationship])
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
      contactsMap <- getUsersByIdList(fields, ids: _*)
    } yield (contactsMap.values.toSeq map (_.orNull)) filter (_ != null)
  }

  /**
   * 给定一个ContactRequest，生成相应的MongoDB update operation
   * @param req
   * @param ds
   * @return
   */
  private def buildContactRequestUpdateOps(req: ContactRequest)(implicit ds: Datastore): UpdateOperations[ContactRequest] = {
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
   * 发送好友请求
   * @param sender    请求发送者
   * @param receiver  请求接收者
   * @param message   请求附言
   * @return
   */
  def sendContactRequest(sender: Long, receiver: Long, message: Option[String] = None)
                        (implicit ds: Datastore, futurePool: FuturePool): Future[ObjectId] = {
    // 检查用户是否存在
    val responseFields:Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    for {
      users <- getUsersByIdList(responseFields, sender, receiver)(ds, futurePool)
      relationship <- isContact(sender, receiver)(ds, futurePool)
    } yield {
      if (relationship)
        throw InvalidStateException(s"$sender and $receiver are already contacts")
      else if (users filter (_._2 isEmpty) nonEmpty)
        throw NotFoundException(s"Either $sender or $receiver cannot be found")
      else {
        import ContactRequest.RequestStatus._
        import ContactRequest._

        val req = ContactRequest(sender, receiver, message, None)

        // 只有在下列情况下，可以发送好友申请
        // * 从未发送过好友申请
        // * 发送过好友申请，且前一申请的状态CANCELLED
        // * 发送过好友申请，且前一申请的状态为PENDING，且已经过期
        val currentTime = req.timestamp
        val cls = classOf[ContactRequest]
        val query = ds.createQuery(cls).field(fdSender).equal(sender).field(fdReceiver).equal(receiver)

        val criteria1 = ds.createQuery(cls).criteria(fdStatus).equal(CANCELLED.id)
        val criteria2 = ds.createQuery(cls).criteria(fdStatus).equal(PENDING.id)
        val criteria3 = ds.createQuery(cls).criteria(fdExpire).lessThan(currentTime)

        query.and(ds.createQuery(cls).or(
          criteria1,
          ds.createQuery(cls).and(criteria2, criteria3)))

        val updateOps = buildContactRequestUpdateOps(req)
        val newRequest = try {
          ds.findAndModify(query, updateOps, false, true)
        } catch {
          // 如果发生该异常，说明系统中已经存在一个好友请求，且不允许重复发送请求
          // 比如：前一个请求处于PENDING状态，且未过期，或者前一个请求已经被拒绝等）
          case ex: MongoCommandException =>
            if (isDuplicateKeyException(ex))
              throw InvalidStateException("")
            else
              throw ex
        }
        // 触发发送好友请求
        val senderInfo = users(sender).get
        val receiverInfo = users(receiver).get
        val miscInfo = new ObjectMapper().createObjectNode()
        val eventArgs = scala.collection.immutable.Map(
          "requestId" -> TextNode.valueOf(newRequest.id.toString),
          "message" -> TextNode.valueOf(message.getOrElse("")),
          "sender" -> user2JsonNode(senderInfo),
          "receiver" -> user2JsonNode(receiverInfo),
          "miscInfo" -> miscInfo
        )
        EventEmitter.emitEvent(EventEmitter.evtSendContactRequest, eventArgs)
        newRequest.id
      }
    }
  }

  /**
   * 拒绝一个好友请求
   * @param requestId 请求ID
   * @param message   拒绝请求的附言
   * @return
   */
  def rejectContactRequest(requestId: String, message: Option[String] = None)
                          (implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    import ContactRequest.RequestStatus._
    import ContactRequest._

    getContactRequest(requestId)(ds, futurePool) map (oldRequest => {
      if (oldRequest isEmpty)
        throw NotFoundException(s"Cannot find the request $requestId")
      else {
        val cls = classOf[ContactRequest]
        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
          .field(fdStatus).equal(PENDING.id)
        val updateOps = ds.createUpdateOperations(cls).set(fdStatus, REJECTED.id)
        if (message nonEmpty)
          updateOps.set(fdRejectMessage, message.get)

        val newRequest = ds.findAndModify(query, updateOps, false, false)
        if (newRequest == null)
          throw InvalidStateException("")

        // 触发拒绝好友请求
        val responseFields:Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
        val senderId = oldRequest.get.sender
        val receiverId = oldRequest.get.receiver
        val users = getUsersByIdList(responseFields, senderId, receiverId)
        for{
          userInfos <- users
        } yield {
          val miscInfo = new ObjectMapper().createObjectNode()
          val eventArgs = scala.collection.immutable.Map(
            "requestId" -> TextNode.valueOf(newRequest.id.toString),
            "message" -> TextNode.valueOf(message.getOrElse("")),
            "sender" -> user2JsonNode(userInfos(senderId).get),
            "receiver" -> user2JsonNode(userInfos(receiverId).get),
            "miscInfo" -> miscInfo
          )
          EventEmitter.emitEvent(EventEmitter.evtRejectContactRequest, eventArgs)
        }
      }
    })
  }

  /**
   * 接受一个好友请求
   * @param requestId 请求ID
   * @return
   */
  def acceptContactRequest(requestId: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    import ContactRequest.RequestStatus._
    import ContactRequest._

    getContactRequest(requestId = requestId)(ds, futurePool) flatMap (oldRequest => {
      if (oldRequest isEmpty)
        throw NotFoundException(s"Cannot find the request $requestId")
      else {
        val cls = classOf[ContactRequest]

        val currentTime = System.currentTimeMillis()
        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
          .field(fdStatus).equal(PENDING.id).field(fdExpire).greaterThan(currentTime)
        val updateOps = ds.createUpdateOperations(cls).set(fdStatus, ACCEPTED.id)

        val newRequest = ds.findAndModify(query, updateOps, false, false)
        if (newRequest == null)
          throw InvalidStateException("")

        addContact(newRequest.sender, newRequest.receiver)
        // 触发拒绝好友请求
        val responseFields:Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
        val senderId = oldRequest.get.sender
        val receiverId = oldRequest.get.receiver
        val users = getUsersByIdList(responseFields, senderId, receiverId)
        for{
          userInfos <- users
        } yield {
          val miscInfo = new ObjectMapper().createObjectNode()
          val eventArgs = scala.collection.immutable.Map(
            "requestId" -> TextNode.valueOf(newRequest.id.toString),
            "sender" -> user2JsonNode(userInfos(senderId).get),
            "receiver" -> user2JsonNode(userInfos(receiverId).get),
            "miscInfo" -> miscInfo
          )
          EventEmitter.emitEvent(EventEmitter.evtAcceptContactRequest, eventArgs)
        }
      }
    })
  }

  /**
   * 取消一个好友请求
   * @param requestId 请求ID
   * @return
   */
  def cancelContactRequest(requestId: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    import ContactRequest.RequestStatus._
    import ContactRequest._

    getContactRequest(requestId = requestId)(ds, futurePool) map (oldRequest => {
      if (oldRequest isEmpty)
        throw NotFoundException(s"Cannot find the request $requestId")
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
  def getContactRequest(sender: Long, receiver: Long)(implicit ds: Datastore, futurePool: FuturePool)
  : Future[Option[ContactRequest]] = futurePool {
    import ContactRequest._
    val req = ds.createQuery(classOf[ContactRequest]).field(fdSender).equal(sender).field(fdReceiver).equal(receiver).get()
    if (req == null)
      throw NotFoundException("Cannot find the request")
    else
      Option(req)
  }

  /**
   * 获得好友请求的详情
   * @param requestId
   * @return
   */
  def getContactRequest(requestId: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Option[ContactRequest]] =
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
  def getContactCount(userId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Int] = {
    val userFuture = getUserById(userId)

    userFuture map (userInfo => {
      if (userInfo isEmpty)
        throw NotFoundException(s"User not find $userId")
      else {
        val criteria = Seq(Relationship.fdUserA, Relationship.fdUserB) map
          (f => ds.createQuery(classOf[Relationship]).criteria(f).equal(userId))
        val query = ds.createQuery(classOf[Relationship])
        query.or(criteria: _*)
        query.countAll().toInt
      }
    })
  }

  /**
   * 批量获得多个用户的信息
   *
   * @param userIds 需要查找的用户的ID
   * @return
   */
  def getUsersByIdList(fields: Seq[UserInfoProp], userIds: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Map[Long, Option[UserInfo]]] = {
    futurePool {
      if (userIds isEmpty) {
        Map[Long, Option[UserInfo]]()
      } else {
        val query = userIds length match {
          case 1 => ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(userIds head)
          case _ => ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(userIds)
        }
        // 获得需要处理的字段名
        val allowedProperties = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar,
          UserInfoProp.Signature, UserInfoProp.Gender, UserInfoProp.Tel)
        val retrievedFields = (fields filter (allowedProperties.contains(_))) :+ UserInfoProp.UserId map userInfoPropToFieldName

        query.retrievedFields(true, retrievedFields: _*)
        val results = Map(query.asList() map (v => v.userId -> v): _*)
        Map(userIds map (v => v -> (results get v)): _*)
      }
    }
  }

  /**
   * 用户登录
   *
   * @param loginName   登录用户名（默认情况下是注册的手机号）
   * @param password    登录密码
   * @return
   */
  def login(loginName: String, password: String, source: String)(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] = {
    // 获得用户信息
    val userInfo = futurePool {
      ds.find(classOf[UserInfo], UserInfo.fdTel, loginName).get()
    }

    // 获得机密信息
    val complex = userInfo map (v => {
      if (v != null) {
        val userId = v.userId
        (v, ds.find(classOf[Credential], Credential.fdUserId, userId).get())
      } else throw new AuthException("")
    })

    // 验证
    val result = complex map (v => {
      val (user, credential) = v
      val crypted = saltPassword(password, Some(credential.salt))._2
      if (crypted == credential.passwdHash)
        user
      else
        throw new AuthException("")
    })

    // 触发登录事件
    result map (v => {
      val miscInfo = new ObjectMapper().createObjectNode()
      val eventArgs = scala.collection.immutable.Map(
        "user" -> user2JsonNode(v),
        "source" -> TextNode.valueOf(source),
        "miscInfo" -> miscInfo
      )
      EventEmitter.emitEvent(EventEmitter.evtLogin, eventArgs)
      v
    })
  }

  // 新用户注册
  def createUser(nickName: String, password: String, tel: Option[String])(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] = {
    // 取得用户ID
    val futureUserId = IdGenerator.generateId("yunkai.newUserId")

    // 创建用户并保存
    val userInfo = for {
      userId <- futureUserId
    } yield {
        val newUser = UserInfo(userId, nickName)
        newUser.tel = tel.orNull
        try {
          ds.save[UserInfo](newUser)
          newUser
        } catch {
          case ex: DuplicateKeyException => throw new UserExistsException(s"User $userId is existed")
        }
      }

    val (salt, crypted) = saltPassword(password)

    // 创建并保存新用户Credential实例
    for {
      userId <- futureUserId
    } yield {
      val credential = Credential(userId, salt, crypted)
      try {
        ds.save[Credential](credential)
      } catch {
        case ex: DuplicateKeyException => throw new InvalidArgsException(s"User $userId credential is existed")
      }
    }

    // 触发创建新用户的事件
    val miscInfo = new ObjectMapper().createObjectNode()
    userInfo map (v => {
      val eventArgs = scala.collection.immutable.Map(
        "user" -> user2JsonNode(v),
        "miscInfo" -> miscInfo
      )
      EventEmitter.emitEvent(EventEmitter.evtCreateUser, eventArgs)
    })

    userInfo
  }

  def updatePassword(userId: Long, newPassword: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val query = ds.find(classOf[Credential], Credential.fdUserId, userId)
    if (query isEmpty) throw new NotFoundException(s"User userId=$userId credential is not found")
    else {
      val (salt, crypted) = saltPassword(newPassword)
      // 更新Credential
      val updateOps = ds.createUpdateOperations(classOf[Credential]).set(Credential.fdSalt, salt)
        .set(Credential.fdPasswdHash, crypted)
      ds.updateFirst(query, updateOps)
    }

    // 触发重置用户密码的事件
    val responseFields:Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    val user = getUserById(userId, responseFields)
    for{
      elem <- user
    }yield {
      val userInfo = elem.get
      val miscInfo = new ObjectMapper().createObjectNode()
      val eventArgs = scala.collection.immutable.Map(
        "user" -> user2JsonNode(userInfo),
        "miscInfo" -> miscInfo
      )
      EventEmitter.emitEvent(EventEmitter.evtResetPassword, eventArgs)
      }
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

  def searchUserInfo(queryFields: Map[UserInfoProp, String], fields: Option[Seq[UserInfoProp]], offset: Option[Int] = None,
                     count: Option[Int] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[UserInfo]] = {
    val query = ds.createQuery(classOf[UserInfo])

    val criteriaList = queryFields.toSeq map (item => {
      item._1 match {
        case UserInfoProp.Tel => ds.createQuery(classOf[UserInfo]).criteria(UserInfo.fdTel).startsWith(item._2)
        case UserInfoProp.NickName => ds.createQuery(classOf[UserInfo]).criteria(UserInfo.fdNickName).startsWithIgnoreCase(item._2)
        //case UserInfoProp.Gender => ds.createQuery(classOf[UserInfo]).criteria(UserInfo.fdGender).equal(item._2)
        case _ => null
      }
    })  filter (_!=null)

    query.or(criteriaList:_*)

    // 分页
    val defaultOffset = 0
    val defaultCount = 20

    // 限定查询返回字段
    val retrievedFields = fields.getOrElse(Seq()) map {
      case UserInfoProp.UserId => UserInfo.fdUserId
      case UserInfoProp.NickName => UserInfo.fdNickName
      case UserInfoProp.Avatar => UserInfo.fdAvatar
      case UserInfoProp.Tel => UserInfo.fdTel
      case _ => ""
    } filter (_ nonEmpty)

    // 获得符合条件的userId
    futurePool {
      query.offset(offset.getOrElse(defaultOffset)).limit(count.getOrElse(defaultCount))
      if (retrievedFields nonEmpty)
        query.retrievedFields(true, retrievedFields :+ UserInfo.fdUserId: _*)
      query.asList().toSeq
    }
  }
}
