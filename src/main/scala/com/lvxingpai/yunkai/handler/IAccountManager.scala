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

  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, Any]): Future[UserInfo]

  def isContact(userA: Long, userB: Long): Future[Boolean]

  def addContact(userId: Long, targetUsers: Long*): Future[Unit]

  def removeContacts(userId: Long, targetUsers: Long*): Future[Unit]

  def getContactList(userId: Long, include: Boolean = true, fields: Seq[UserInfoProp] = Seq(), offset: Option[Int] = None,
    count: Option[Int] = None): Future[Seq[yunkai.UserInfo]]

  def updateMemo(userA: Long, userB: Long, memo: String): Future[Unit]

  def searchUserInfo(queryFields: Map[UserInfoProp, String], fields: Option[Seq[UserInfoProp]], offset: Option[Int] = None,
    count: Option[Int] = None): Future[Seq[yunkai.UserInfo]]

  def getContactCount(userId: Long): Future[Int]

  def login(loginName: String, password: String, source: String): Future[UserInfo]

  def resetPassword(userId: Long, oldPassword: String, newPassword: String): Future[Unit]

  def resetPasswordByToken(userId: Long, newPassword: String, token: String): Future[Unit]

  def loginByWeixin(code: String, source: String): Future[yunkai.UserInfo]

  def isBlocked(selfId: Long, targetId: Long): Future[Boolean]

  def updateBlackList(userA: Long, userB: Long, block: Boolean): Future[Unit]

}
