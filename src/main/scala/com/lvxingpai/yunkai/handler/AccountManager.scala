package com.lvxingpai.yunkai.handler

import java.security.MessageDigest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ LongNode, NullNode, TextNode }
import com.lvxingpai.yunkai
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.{ Credential, Relationship, UserInfo }
import com.mongodb.DuplicateKeyException
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.CriteriaContainer

import scala.collection.JavaConversions._
import scala.collection.Map
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
object AccountManager {

  /**
   * 在models.UserInfo和yunkai.UserInfo之间进行类型转换
   *
   * @param user
   * @return
   */
  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
    val userId = user.userId
    val nickName = user.nickName
    val avatar = if (user.avatar == null) None else Some(user.avatar)
    val gender = None
    val signature = None
    val tel = None

    yunkai.UserInfo(userId, nickName, avatar, gender, signature, tel)
  }

  /**
   * 获得用户信息
   *
   * @param include 和fields配合使用。表示是返回fields字段的内容，还是排除fields字段的内容
   *
   * @return 用户信息
   */
  def getUserById(userId: Long, include: Boolean = true, fields: Seq[UserInfoProp])(implicit ds: Datastore, futurePool: FuturePool): Future[Option[UserInfo]] =
    futurePool {
      val query = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(userId)

      // 获得需要处理的字段名
      val fieldNames = fields map userInfoPropToFieldName

      val user = query.retrievedFields(include, fieldNames: _*).get()
      if (user == null) None else Some(user)
    }

  /**
   * 更新用户信息
   *
   * @param userId    需要更新的用户Id
   * @param userInfo  需要更新的用户信息
   * @return
   */
  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, Any])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    // 只允许更新一小部分字段信息
    val allowedFields = Seq(UserInfoProp.NickName, UserInfoProp.Signature)
    val filteredUserInfo = userInfo filter (item => allowedFields contains item._1)

    if (filteredUserInfo nonEmpty) {
      val query = ds.find(classOf[UserInfo], "userId", userId)
      val updateOps = filteredUserInfo.foldLeft(ds.createUpdateOperations(classOf[UserInfo]))((ops, entry) => {
        val (key, value) = entry
        ops.set(key, value)
      })

      val updateResult = ds.updateFirst(query, updateOps)
      if (!updateResult.getUpdatedExisting)
        throw NotFoundException(s"Cannot find user: $userId")
    }
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
  def addContact(userId: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      val cls = classOf[Relationship]

      for (target <- targetUsers) {
        val (user1, user2) = if (userId <= target) (userId, target) else (target, userId)
        val op = ds.createUpdateOperations(cls).set(Relationship.fdUserA, user1).set(Relationship.fdUserB, user2)
        val query = ds.createQuery(cls).field(Relationship.fdUserA).equal(user1)
          .field(Relationship.fdUserB).equal(user2)
        ds.updateFirst(query, op, true)
      }
    }

  /**
   * 删除好友
   *
   * @param userId      需要被删除好友的用户的ID
   * @param targetUsers 需要被删除的好友的ID
   *
   * @return
   */
  def removeContacts(userId: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      def buildQuery(user1: Long, user2: Long): CriteriaContainer = {
        val l = Seq(user1, user2).sorted
        ds.createQuery(classOf[Relationship]).criteria(Relationship.fdUserA).equal(l head)
          .criteria(Relationship.fdUserB).equal(l last)
      }

      val query = ds.createQuery(classOf[Relationship])
      query.or(targetUsers map (buildQuery(userId, _)): _*)
      ds.delete(query)
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
  def getContactList(userId: Long, include: Boolean = true, fields: Seq[UserInfoProp], offset: Option[Int] = None,
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
      contactsMap <- getUsersByIdList(include, fields, ids: _*)
    } yield (contactsMap.values.toSeq map (_.orNull)) filter (_ != null)
  }

  /**
   * 批量获得多个用户的信息
   *
   * @param include 和fields配合使用。表示是返回fields字段的内容，还是排除fields字段的内容
   * @param userIds 需要查找的用户的ID
   * @return
   */
  def getUsersByIdList(include: Boolean, fields: Seq[UserInfoProp], userIds: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Map[Long, Option[UserInfo]]] = {
    futurePool {
      if (userIds isEmpty) {
        Map[Long, Option[UserInfo]]()
      } else {
        val query = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(userIds)
        val retrievedFields = fields map userInfoPropToFieldName
        query.retrievedFields(include, retrievedFields: _*)
        val results = Map(query.asList() map (v => v.userId -> v): _*)
        Map(userIds map (v => v -> (results get v)): _*)
      }
    }
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
      case _ => throw new IllegalArgumentException("Illegal arguemnt: %s" format prop.toString)
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
      val salt = credential.salt
      val encrypted = credential.passwdHash
      val msg = salt + password
      val bytes = MessageDigest.getInstance("SHA-256").digest(msg.getBytes)
      val digest = bytes map ("%02x".format(_)) mkString

      if (digest == encrypted) user
      else throw new AuthException("")
    })

    // 触发登录事件
    result map (v => {
      val miscInfo = new ObjectMapper().createObjectNode()
      miscInfo.put("nickName", v.nickName)
      miscInfo.put("avatar", v.avatar)
      val eventArgs = scala.collection.immutable.Map(
        "userId" -> LongNode.valueOf(v.userId),
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
        case ex: DuplicateKeyException => throw new InvalidArgsException(s"User $userId is existed")
      }
    }
    // 生成64个字节的salt
    val md5 = MessageDigest.getInstance("MD5")
    //使用指定的字节更新摘要
    md5.update(Random.nextLong().toString.getBytes)
    val salt = md5.digest().toString

    // 将密码与salt一起生成密文
    val msg = salt + password
    val bytes = MessageDigest.getInstance("SHA-256").digest(msg.getBytes)
    val digest = bytes map ("%02x".format(_)) mkString

    // 创建并保存新用户Credential实例
    for {
      userId <- futureUserId
    } yield {
      val credential = Credential(userId, salt, digest)
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
}
