package com.lvxingpai.yunkai.handler

import java.security.MessageDigest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{LongNode, NullNode, TextNode}
import com.lvxingpai.yunkai
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.{Credential, Relationship, UserInfo}
import com.mongodb.DuplicateKeyException
import com.twitter.util.{Future, FuturePool}
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.CriteriaContainer

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
      else{
        // 触发修改个人信息事件
        // 修改了哪些字段
        val updated = new ObjectMapper().createObjectNode()
        val eventArgs = scala.collection.immutable.Map(
          "userId" -> LongNode.valueOf(result.userId),
          "nickName" -> TextNode.valueOf(result.nickName),
          "avatar" -> (if (result.avatar != null && result.avatar.nonEmpty) TextNode.valueOf(result.avatar) else NullNode.getInstance()),
          "updated" -> updated
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
    getUsersByIdList(Seq(UserInfoProp.UserId), userId +: targetUsersFiltered: _*) map (m => {
      // 相应的用户必须存在
      if ((m filter (_._2 isEmpty)).toSeq.length != 0)
        throw NotFoundException("")
      else {
        val cls = classOf[Relationship]

        for (target <- targetUsersFiltered) {
          val (user1, user2) = if (userId <= target) (userId, target) else (target, userId)
          val op = ds.createUpdateOperations(cls).set(Relationship.fdUserA, user1).set(Relationship.fdUserB, user2)
          val query = ds.createQuery(cls).field(Relationship.fdUserA).equal(user1)
            .field(Relationship.fdUserB).equal(user2)
          ds.updateFirst(query, op, true)
        }
      }
    })
    // 触发添加联系人的事件
    // userB的返回字段
    val responseFields:Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    getUsersByIdList(responseFields, targetUsersFiltered: _*) map (item => {
      val userBInfos = item.values.toSeq
      for(elem <- userBInfos) {
        for{
          userA <- getUserById(userId, responseFields)
        }yield {
          val userAInfo = userA.get
          val userBInfo = elem.get
          val eventArgs = scala.collection.immutable.Map(
            "userA" -> LongNode.valueOf(userId),
            "nickNameA" -> TextNode.valueOf(userAInfo.nickName),
            "avatarA" -> (if (userAInfo.avatar != null && userAInfo.avatar.nonEmpty) TextNode.valueOf(userAInfo.avatar) else NullNode.getInstance()),
            "userB" -> LongNode.valueOf(userBInfo.userId),
            "nickNameB" -> TextNode.valueOf(userBInfo.nickName),
            "avatarB" -> (if (userBInfo.avatar != null && userBInfo.avatar.nonEmpty) TextNode.valueOf(userBInfo.avatar) else NullNode.getInstance())
          )
          EventEmitter.emitEvent(EventEmitter.evtAddContacts, eventArgs)
          }
        }
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
  def removeContacts(userId: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    val targetUsersFiltered = (targetUsers filter (_ != userId)).toSet.toSeq
    getUsersByIdList(Seq(UserInfoProp.UserId), userId +: targetUsersFiltered: _*) map (m => {
      // 相应的用户必须存在
      if ((m filter (_._2 isEmpty)).toSeq.length != 0)
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
      }
    })
    // 触发删除联系人的事件
    // userB的返回字段
    val responseFields:Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    getUsersByIdList(responseFields, targetUsersFiltered: _*) map (item => {
      val userBInfos = item.values.toSeq
      for(elem <- userBInfos) {
        for{
          userA <- getUserById(userId, responseFields)
        }yield {
          val userAInfo = userA.get
          val userBInfo = elem.get
          val eventArgs = scala.collection.immutable.Map(
            "userA" -> LongNode.valueOf(userId),
            "nickNameA" -> TextNode.valueOf(userAInfo.nickName),
            "avatarA" -> (if (userAInfo.avatar != null && userAInfo.avatar.nonEmpty) TextNode.valueOf(userAInfo.avatar) else NullNode.getInstance()),
            "userB" -> LongNode.valueOf(userBInfo.userId),
            "nickNameB" -> TextNode.valueOf(userBInfo.nickName),
            "avatarB" -> (if (userBInfo.avatar != null && userBInfo.avatar.nonEmpty) TextNode.valueOf(userBInfo.avatar) else NullNode.getInstance())
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
  def login(loginName: String, password: String)(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] = {
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
      miscInfo.put("avatar", v.avatar)
      val eventArgs = scala.collection.immutable.Map(
        "userId" -> LongNode.valueOf(v.userId),
        "nickName" -> TextNode.valueOf(v.nickName),
        "info" -> miscInfo
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
    userInfo map (v => {
      val eventArgs = scala.collection.immutable.Map(
        "userId" -> LongNode.valueOf(v.userId),
        "nickName" -> TextNode.valueOf(v.nickName),
        "avatar" -> (if (v.avatar != null && v.avatar.nonEmpty) TextNode.valueOf(v.avatar) else NullNode.getInstance())
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
      val eventArgs = scala.collection.immutable.Map(
        "userId" -> LongNode.valueOf(userId),
        "nickName" -> TextNode.valueOf(userInfo.nickName),
        "avatar" -> (if (userInfo.avatar != null && userInfo.avatar.nonEmpty) TextNode.valueOf(userInfo.avatar) else NullNode.getInstance())
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
    queryFields foreach (item => {
      item._1 match {
        case UserInfoProp.Tel => query.or(ds.createQuery(classOf[UserInfo]).criteria(UserInfo.fdTel).startsWith(item._2))
        case UserInfoProp.NickName => query.or(ds.createQuery(classOf[UserInfo]).criteria(UserInfo.fdNickName).contains(item._2))
        case UserInfoProp.Gender => query.or(ds.createQuery(classOf[UserInfo]).criteria(UserInfo.fdGender).contains(item._2))
        case _ => ""
      }
    })

    // 分页
    val defaultOffset = 0
    val defaultCount = 20

    // 限定查询返回字段
    val retrievedFields = fields.getOrElse(Seq()) map {
      case UserInfoProp.UserId => UserInfo.fdUserId
      case UserInfoProp.NickName => UserInfo.fdNickName
      case UserInfoProp.Avatar => UserInfo.fdAvatar
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
