package com.aizou.yunkai.handler

import com.aizou.yunkai.{ UserInfo, Userservice }
import com.twitter.util.Future

/**
 * Created by zephyre on 5/4/15.
 */
class UserInfoHandler extends Userservice.FutureIface {
  override def getUserById(userId: Long): Future[UserInfo] = Future {
    //    val ds = MorphiaFactory.getDatastore("user")
    //    val user = ds.find(classOf[UserInfo], "userId", 100001).get()
    //    UserInfo(userId, Some("haizi"), None, Some(Gender.Male), None, None)
    null
  }
}
