package com.lvxingpai.yunkai.handler

import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.UserInfoProp
import com.lvxingpai.yunkai.model.UserInfo
import com.twitter.util.Future

/**
 * Created by pengyt on 2015/9/18.
 */
trait IAccountManager {

  def getUserById(userId: Long, fields: Seq[UserInfoProp], selfId: Option[Long]): Future[Option[yunkai.UserInfo]]

  def createUser(nickName: String, password: String, tel: Option[String]): Future[UserInfo]

  def getUsersByIdList(fields: Seq[UserInfoProp], selfId: Option[Long], userIds: Long*): Future[Map[Long, Option[yunkai.UserInfo]]]

}
