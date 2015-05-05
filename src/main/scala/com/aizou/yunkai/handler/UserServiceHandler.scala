package com.aizou.yunkai.handler

import com.aizou.yunkai
import com.aizou.yunkai.database.mongo.MorphiaFactory
import com.aizou.yunkai.model.{ Relationship, UserInfo }
import com.aizou.yunkai.{ NotFoundException, UserInfoProp, Userservice }
import com.twitter.util.Future
import org.mongodb.morphia.query.CriteriaContainer

import scala.collection.Map

/**
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {
  override def getUserById(userId: Long): Future[yunkai.UserInfo] = {
    val ds = MorphiaFactory.getDatastore()
    val entry = ds.find(classOf[UserInfo], "userId", userId).get()

    Future {
      if (entry == null) throw new NotFoundException(s"User not found for userId=$userId")
      else {
        def toOption[T](value: T): Option[T] = if (value != null) Some(value) else None
        yunkai.UserInfo(entry.userId, entry.nickName, toOption(entry.avatar), None, None, None)
      }
    }
  }

  override def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String]): Future[Unit] = Future {
    val ds = MorphiaFactory.getDatastore()
    val updateOps = ds.createUpdateOperations(classOf[UserInfo])
    val query = ds.find(classOf[UserInfo], "userId", userId)

    userInfo foreach ((item: (UserInfoProp, String)) => {
      val key = item._1
      val value = item._2
      key match {
        case UserInfoProp.NickName => updateOps.set("nickName", value)
        case UserInfoProp.Signature => updateOps.set("signature", value)
        case _ => updateOps
      }
    })

    ds.updateFirst(query, updateOps)
  }

  override def isContact(userA: Long, userB: Long): Future[Boolean] = Future {
    val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
    val ds = MorphiaFactory.getDatastore()
    ds.createQuery(classOf[Relationship]).field("userA").equal(user1).field("userB").equal(user2).get() != null
  }

  override def addContact(userA: Long, userB: Long): Future[Unit] = Future {
    val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
    val ds = MorphiaFactory.getDatastore()
    val op = ds.createUpdateOperations(classOf[Relationship]).set("userA", user1).set("userB", user2)
    val query = ds.createQuery(classOf[Relationship]).field("userA").equal(user1).field("userB").equal(user2)
    ds.updateFirst(query, op, true)
  }

  override def removeContact(userA: Long, userList: Seq[Long]): Future[Unit] = Future {
    val ds = MorphiaFactory.getDatastore()

    def buildQuery(user1: Long, user2: Long): CriteriaContainer = {
      val l = Seq(user1, user2).sorted
      ds.createQuery(classOf[Relationship]).criteria("userA").equal(l head).criteria("userB").equal(l last)
    }

    val query = ds.createQuery(classOf[Relationship])
    query.or(userList map (buildQuery(userA, _)): _*)
    ds.delete(query)
  }
}
