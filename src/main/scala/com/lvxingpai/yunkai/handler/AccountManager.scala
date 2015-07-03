package com.lvxingpai.yunkai.handler

import java.security.MessageDigest
import java.util.UUID

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.{ContactRequest, UserInfo, _}
import com.lvxingpai.yunkai.serialization.{ValidationCodeRedisFormat, ValidationCodeRedisParse}
import com.lvxingpai.yunkai.service.{RedisFactory, SmsCenter}
import com.mongodb.{DuplicateKeyException, MongoCommandException}
import com.twitter.util.{Future, FuturePool}
import org.bson.types.ObjectId
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.{CriteriaContainer, UpdateOperations}

import scala.collection.JavaConversions._
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
      case UserInfoProp.Id => UserInfo.fdId
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
        throw new InvalidArgsException(Some(s"Invalid gender $gender"))
    }

    // 获得需要处理的字段名
    val fieldNames = ((filteredUserInfo.keys.toSeq ++ Seq(UserInfoProp.UserId, UserInfoProp.Id))
      map userInfoPropToFieldName)

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
        throw NotFoundException(Some(s"Cannot find user: $userId"))
      else {
        // 触发修改个人信息事件
        val eventArgs: Map[String, JsonNode] = Map(
          "user" -> result
        )
        EventEmitter.emitEvent(EventEmitter.evtModUserInfo, eventArgs)
        // 返回userInfo
        result
      }
    } else
      throw new InvalidArgsException(Some("Invalid updated fields"))
  }

  /**
   * 判断两个用户是否为好友
   *
   * @return
   */
  def isContact(userA: Long, userB: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Boolean] = {
    val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)

    val userList = getUsersByIdList(Seq(), user1, user2)
    val relationship = futurePool {
      val rel = ds.createQuery(classOf[Relationship]).field(Relationship.fdUserA).equal(user1)
        .field(Relationship.fdUserB).equal(user2)
        .retrievedFields(true, Relationship.fdId)
        .get
      rel != null
    }

    for {
      l <- userList
      rel <- relationship
    } yield {
      if (l exists (_._2 isEmpty))
        throw NotFoundException()
      else
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
  def addContact(userId: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    val targetUsersFiltered = (targetUsers filter (_ != userId)).toSet.toSeq
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)

    getUsersByIdList(responseFields, userId +: targetUsersFiltered: _*) flatMap (m => {
      // 相应的用户必须存在
      if (m exists (_._2 isEmpty))
        throw NotFoundException()
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
        val eventArgs: Map[String, JsonNode] = Map(
          "user" -> sender,
          "targets" -> receiver
        )
        EventEmitter.emitEvent(EventEmitter.evtAddContacts, eventArgs)
      })

      Future.collect(jobs) map (_ => ())
    })
  }

  implicit def user2JsonNode(user: UserInfo): JsonNode = {
    val targets = new ObjectMapper().createObjectNode()
    targets.put("id", user.id.toString)
    targets.put("userId", user.userId)
    targets.put("nickName", user.nickName)
    val avatarValue = Option(user.avatar).getOrElse("")
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
        throw NotFoundException()
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
        for (elem <- targetUsersFiltered) {
          val userBInfo = m(elem).get
          val eventArgs: Map[String, JsonNode] = Map(
            "user" -> userAInfo,
            "targets" -> userBInfo
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
    } yield contactsMap.toSeq.map(_._2.orNull) filter (_ != null)
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

  def getContactRequestList(userId: Long, offset: Int, limit: Int)
                           (implicit ds: Datastore, futurePool: FuturePool): Future[Seq[ContactRequest]] = {
    for {
      userInfoOpt <- getUserById(userId)
    } yield {
      if (userInfoOpt isEmpty)
        throw NotFoundException()
      else {
        ds.createQuery(classOf[ContactRequest]).field(ContactRequest.fdReceiver).equal(userId)
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
  def sendContactRequest(sender: Long, receiver: Long, message: Option[String] = None)
                        (implicit ds: Datastore, futurePool: FuturePool): Future[ObjectId] = {
    // 检查用户是否存在
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    for {
      users <- getUsersByIdList(responseFields, sender, receiver)
      relationship <- isContact(sender, receiver)
    } yield {
      if (relationship)
        throw InvalidStateException(Some(s"$sender and $receiver are already contacts"))
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
        import Implicits.JsonConversions._
        val senderInfo = users(sender).get
        val receiverInfo = users(receiver).get
        val eventArgs: Map[String, JsonNode] = Map(
          "requestId" -> newRequest.id.toString,
          "message" -> message.orNull[String],
          "sender" -> senderInfo,
          "receiver" -> receiverInfo
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
        throw NotFoundException(Some(s"Cannot find the request $requestId"))
      else {
        val cls = classOf[ContactRequest]
        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
          .field(fdStatus).equal(PENDING.id)
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
        val users = getUsersByIdList(responseFields, senderId, receiverId)
        for {
          userInfos <- users
        } yield {
          import Implicits.JsonConversions._
          val eventArgs: Map[String, JsonNode] = Map(
            "requestId" -> newRequest.id.toString,
            "message" -> message.orNull[String],
            "sender" -> userInfos(senderId).get,
            "receiver" -> userInfos(receiverId).get
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
        throw NotFoundException(Some(s"Cannot find the request $requestId"))
      else {
        val cls = classOf[ContactRequest]

        val query = ds.createQuery(cls).field(fdContactRequestId).equal(new ObjectId(requestId))
          .field(fdStatus).equal(PENDING.id)
        val updateOps = ds.createUpdateOperations(cls).set(fdStatus, ACCEPTED.id)

        val newRequest = ds.findAndModify(query, updateOps, false, false)
        if (newRequest == null)
          throw InvalidStateException()

        addContact(newRequest.sender, newRequest.receiver)
        // 触发接受好友请求
        val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
        val senderId = oldRequest.get.sender
        val receiverId = oldRequest.get.receiver
        val users = getUsersByIdList(responseFields, senderId, receiverId)
        for {
          userInfos <- users
        } yield {
          import Implicits.JsonConversions._
          val eventArgs: Map[String, JsonNode] = Map(
            "requestId" -> newRequest.id.toString,
            "sender" -> userInfos(senderId).get,
            "receiver" -> userInfos(receiverId).get
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
  def getContactRequest(sender: Long, receiver: Long)(implicit ds: Datastore, futurePool: FuturePool)
  : Future[Option[ContactRequest]] = futurePool {
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
        throw NotFoundException(Some(s"User not find $userId"))
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
        val retrievedFields = (fields filter (allowedProperties.contains(_))) ++ Seq(UserInfoProp.UserId,
          UserInfoProp.Id) map userInfoPropToFieldName

        query.retrievedFields(true, retrievedFields: _*)
        val results = Map(query.asList() map (v => v.userId -> v): _*)
        Map(userIds map (v => v -> (results get v)): _*)
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
  def verifyCredential(userId: Long, password: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Boolean] = {
    val query = ds.createQuery(classOf[Credential]).field(Credential.fdUserId).equal(userId)
    futurePool {
      val credential = query.get()
      credential != null && {
        val crypted = saltPassword(password, Some(credential.salt))._2
        crypted == credential.passwdHash
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
    for {
      userInfo <- futurePool {
        val retrievedFields = Seq(UserInfo.fdId, UserInfo.fdUserId, UserInfo.fdNickName, UserInfo.fdGender, UserInfo.fdAvatar,
          UserInfo.fdSignature, UserInfo.fdTel)
        ds.createQuery(classOf[UserInfo]).field(UserInfo.fdTel).equal(loginName)
          .retrievedFields(true, retrievedFields: _*).get()
      }
      verified <- {
        if (userInfo == null)
          futurePool(false)
        else
          verifyCredential(userInfo.userId, password)
      }
    } yield {
      if (verified) {
        import Implicits.JsonConversions._
        val eventArgs: Map[String, JsonNode] = Map(
          "user" -> userInfo,
          "source" -> source
        )
        EventEmitter.emitEvent(EventEmitter.evtLogin, eventArgs)

        userInfo
      }
      else
        throw AuthException()
    }
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
        newUser.tel = tel.getOrElse(UUID.randomUUID().toString)
        try {
          ds.save[UserInfo](newUser)
          newUser
        } catch {
          case ex: DuplicateKeyException => throw new UserExistsException(Some(s"User $userId is existed"))
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
        case ex: DuplicateKeyException => throw new InvalidArgsException(Some(s"User $userId credential is existed"))
      }
    }

    // 触发创建新用户的事件
    userInfo map (v => {
      val eventArgs: Map[String, JsonNode] = Map(
        "user" -> v
      )
      EventEmitter.emitEvent(EventEmitter.evtCreateUser, eventArgs)
    })

    userInfo
  }

  /**
   * 检查验证码。如果通过的话，返回相应的Token，否则抛出ValidationCodeException异常
   * @return
   */
  def checkValidationCode(valCode: String, action: Int, countryCode: Option[Int] = None, tel: String, userId: Option[Long])
                         (implicit ds: Datastore, futurePool: FuturePool): Future[String] = {
    val fingerprint = ValidationCode.calcFingerprint(action, userId, tel, countryCode)

    futurePool {
      RedisFactory.pool.withClient(client => {
        implicit val parse = ValidationCodeRedisParse()
        val checkResult = client.get[ValidationCode](fingerprint) exists (c => {
          c.expireTime > System.currentTimeMillis && c.code == valCode
        })

        if (checkResult)
          fingerprint
        else
          throw ValidationCodeException()
      })
    }
  }

  def fetchToken(fingerprint: String)
                (implicit ds: Datastore, futurePool: FuturePool): Future[Token] = {
    futurePool {
      RedisFactory.pool.withClient(client => {
        implicit val parse = ValidationCodeRedisParse()
        client.get[ValidationCode](fingerprint) map (code => {
          Token(fingerprint, code.action, code.userId, code.createTime, code.expireTime)
        }) getOrElse (throw NotFoundException())
      })
    }
  }

  def sendValidationCode(action: Int, countryCode: Option[Int] = None, tel: String, userId: Option[Long])
                        (implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    val expire = 10 * 60 * 1000 // 10分钟过期
    val resendInterval = 60 * 1000 // 发送间隔为1分钟
    val rnd = Random.nextInt(1000000)
    val code = ValidationCode(f"$rnd%06d", action, userId, tel, expire, resendInterval, countryCode)
    val fingerprint = code.fingerprint

    futurePool {
      RedisFactory.pool.withClient(client => {
        implicit val format = ValidationCodeRedisFormat()
        implicit val parse = ValidationCodeRedisParse()

        // 是否可以再次发送
        val canSend = client.get[ValidationCode](fingerprint) map (_.resendTime < System.currentTimeMillis) getOrElse true

        if (!canSend)
          throw InvalidStateException()

        client.setex(fingerprint, expire / 1000, code)
      })
    } map (result => {
      if (result) {
        val message = action match {
          case 1 =>
            s"注册旅行派账户。验证码：${code.code}"
        }
        val runlevel = Global.conf.getString("runlevel")
        if (runlevel != "test")
          SmsCenter.client.sendSms(message, Seq(tel))
      }
    })
  }

  /**
   * 重置密码
   * @return
   */
  def resetPassword(userId: Long, oldPassword: String, newPassword: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
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
  private def resetPasswordImpl(userId: Long, newPassword: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {

    def emitEvent(): Future[Unit] = {
      // 触发重置用户密码的事件
      val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
      val user = getUserById(userId, responseFields)
      for {
        elem <- user
      } yield {
        elem foreach (userInfo => {
          val eventArgs: Map[String, JsonNode] = Map(
            "user" -> userInfo
          )
          EventEmitter.emitEvent(EventEmitter.evtResetPassword, eventArgs)
        })
      }
    }

    futurePool {
      val query = ds.find(classOf[Credential], Credential.fdUserId, userId)
      val (salt, crypted) = saltPassword(newPassword)
      // 更新Credential
      val updateOps = ds.createUpdateOperations(classOf[Credential]).set(Credential.fdSalt, salt)
        .set(Credential.fdPasswdHash, crypted)
      ds.updateFirst(query, updateOps)
      emitEvent()
      ()
    }
  }

  /**
   * 根据token修改用户密码
   * @return
   */
  def resetPasswordByToken(userId: Long, token: String, newPassword: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    verifyToken(1, userId, token) flatMap (checked => {
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
  private def verifyToken(action: Int, userId: Long, token: String)(implicit futurePool: FuturePool): Future[Boolean] = {
    futurePool {
      RedisFactory.pool.withClient(client => {
        // 验证token是否有效。判断标准
        // * token存在
        // * action一致
        // * userId一致
        // * 未过期

        implicit val parse = ValidationCodeRedisParse()
        client.get[ValidationCode](token) exists (code => {
          code.action == action && code.userId.nonEmpty && code.userId.get == userId && code.expireTime >
            System.currentTimeMillis
        })
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
  def updateTelNumber(userId: Long, tel: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    import UserInfo._

    val cls = classOf[UserInfo]
    val query = ds.createQuery(cls).field(fdUserId).equal(userId)
    val updateOps = ds.createUpdateOperations(cls).set(fdTel, tel)
    val updated = ds.findAndModify(query, updateOps)
    if (updated == null)
      throw NotFoundException(Some(s"Cannot find user $userId"))
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
   * @param ds
   * @param futurePool
   * @return
   */
  def searchUserInfo(queryFields: Map[UserInfoProp, String], fields: Option[Seq[UserInfoProp]], offset: Option[Int] = None,
                     count: Option[Int] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[UserInfo]] = {
    val cls = classOf[UserInfo]

    val query = ds.createQuery(cls)
    val criteriaList = queryFields.toSeq map (item => {
      item._1 match {
        case UserInfoProp.Tel => ds.createQuery(cls).criteria(UserInfo.fdTel).startsWith(item._2)
        case UserInfoProp.NickName => ds.createQuery(cls).criteria(UserInfo.fdNickName).startsWithIgnoreCase(item._2)
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
        if (retrievedFields nonEmpty)
          query.retrievedFields(true, retrievedFields :+ UserInfo.fdUserId: _*)
        query.asList().toSeq
      }
    }
  }
}
