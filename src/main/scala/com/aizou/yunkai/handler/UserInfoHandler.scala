package com.aizou.yunkai.handler

import com.aizou.yunkai
import com.aizou.yunkai.database.mongo.MorphiaFactory
import com.aizou.yunkai.model.UserInfo
import com.aizou.yunkai.{ NotFoundException, UserInfoProp, Userservice }
import com.twitter.util.Future

import scala.collection.Map

/**
 * Created by zephyre on 5/4/15.
 */
class UserInfoHandler extends Userservice.FutureIface {
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
}
