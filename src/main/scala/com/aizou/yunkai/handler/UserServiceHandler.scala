package com.aizou.yunkai.handler

import com.aizou.yunkai
import com.aizou.yunkai.Implicits._
import com.aizou.yunkai.database.mongo.MorphiaFactory
import com.aizou.yunkai.model.{ Relationship, UserInfo }
import com.aizou.yunkai.{ NotFoundException, UserInfoProp, Userservice }
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.CriteriaContainer

import scala.collection.JavaConversions._
import scala.collection.Map

/**
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {

  override def getUserById(userId: Long): Future[yunkai.UserInfo] = {
    UserServiceHandler.getUserById(userId) map (userInfo => {
      if (userInfo isEmpty) throw new NotFoundException(s"User not found for userId=$userId") else userInfo.get
    })
  }

  override def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String]): Future[Unit] =
    UserServiceHandler.updateUserInfo(userId, userInfo)

  override def isContact(userA: Long, userB: Long): Future[Boolean] = UserServiceHandler.isContact(userA, userB)

  override def addContact(userA: Long, userB: Long): Future[Unit] = UserServiceHandler.addContact(userA, userB)

  override def addContacts(userA: Long, userB: Seq[Long]): Future[Unit] =
    UserServiceHandler.addContact(userA, userB: _*)

  override def removeContact(userA: Long, userB: Long): Future[Unit] = UserServiceHandler.removeContacts(userA, userB)

  override def removeContacts(userA: Long, userList: Seq[Long]): Future[Unit] =
    UserServiceHandler.removeContacts(userA, userList: _*)

  override def getContactList(userId: Long, fields: Option[Seq[UserInfoProp]],
    index: Option[Int], count: Option[Int]): Future[Seq[yunkai.UserInfo]] = {
    val ds = MorphiaFactory.getDatastore()
    val criteria = Seq("userA", "userB") map (f => ds.createQuery(classOf[Relationship]).criteria(f).equal(userId))
    val queryRel = ds.createQuery(classOf[Relationship])
    queryRel.or(criteria: _*)
    val defaultOffset = 0
    val defaultLimit = 1000
    queryRel.offset(index.getOrElse(defaultOffset)).limit(count.getOrElse(defaultLimit))

    // 获得好友关系
    val relList = Future {
      queryRel.asList().toSeq
    }

    // 从好友关系获得用户信息
    def relToUserInfo(relList: Seq[Relationship]): Future[Seq[yunkai.UserInfo]] = {
      // 目标userId
      val targetIds = relList map (rel => if (rel.userA == userId) rel.userB else rel.userA)

      Future {
        if (targetIds nonEmpty) {
          val query = ds.createQuery(classOf[UserInfo]).field("userId").in(targetIds)

          // 限定字段获取的范围
          val retrievedFields = fields.getOrElse(Seq()) map {
            case UserInfoProp.UserId => UserInfo.fdUserId
            case UserInfoProp.NickName => UserInfo.fdNickName
            case UserInfoProp.Avatar => UserInfo.fdAvatar
            case _ => ""
          } filter (_ nonEmpty)

          if (retrievedFields nonEmpty)
            query.retrievedFields(true, retrievedFields: _*)

          query.asList().toSeq map userInfoConversion
        } else Seq()
      }
    }

    relList flatMap relToUserInfo
  }

  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
    val userId = user.userId
    val nickName = user.nickName
    val avatar = if (user.avatar == null) None else Some(user.avatar)
    val gender = None
    val signature = None
    val tel = None

    yunkai.UserInfo(userId, nickName, avatar, gender, signature, tel)
  }

}

object UserServiceHandler {

  def getUserById(userId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Option[yunkai.UserInfo]] =
    futurePool {
      val entry = ds.find(classOf[UserInfo], "userId", userId).get()
      if (entry == null) None
      else {
        def toOption[T](value: T): Option[T] = if (value != null) Some(value) else None
        Some(yunkai.UserInfo(entry.userId, entry.nickName, toOption(entry.avatar), None, None, None))
      }
    }

  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val query = ds.find(classOf[UserInfo], "userId", userId)
    val updateOps = userInfo.foldLeft(ds.createUpdateOperations(classOf[UserInfo]))((ops, entry) => {
      val (key, value) = entry
      key match {
        case UserInfoProp.NickName => ops.set(UserInfo.fdNickName, value)
        case UserInfoProp.Signature => ops.set(UserInfo.fdNickName, value)
        case _ => ops
      }
    })

    ds.updateFirst(query, updateOps)
  }

  def isContact(userA: Long, userB: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Boolean] =
    futurePool {
      val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
      ds.createQuery(classOf[Relationship]).field("userA").equal(user1).field("userB").equal(user2).get() != null
    }

  def addContact(userA: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      val cls = classOf[Relationship]

      for (userB <- targetUsers) {
        val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
        val op = ds.createUpdateOperations(cls).set("userA", user1).set("userB", user2)
        val query = ds.createQuery(cls).field("userA").equal(user1).field("userB").equal(user2)
        ds.updateFirst(query, op, true)
      }
    }

  def removeContacts(userA: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      def buildQuery(user1: Long, user2: Long): CriteriaContainer = {
        val l = Seq(user1, user2).sorted
        ds.createQuery(classOf[Relationship]).criteria("userA").equal(l head).criteria("userB").equal(l last)
      }

      val query = ds.createQuery(classOf[Relationship])
      query.or(targetUsers map (buildQuery(userA, _)): _*)
      ds.delete(query)
    }
}