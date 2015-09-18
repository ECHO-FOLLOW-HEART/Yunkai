package com.lvxingpai.yunkai.handler

import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.{ Role, OperationCode, UserInfoProp }
import com.lvxingpai.yunkai.model.{ ContactRequest, UserInfo }
import com.twitter.util.Future
import org.bson.types.ObjectId

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

  def sendContactRequest(sender: Long, receiver: Long, message: Option[String] = None): Future[ObjectId]

  def rejectContactRequest(requestId: String, message: Option[String] = None): Future[Unit]

  def acceptContactRequest(requestId: String): Future[Unit]

  def cancelContactRequest(requestId: String): Future[Unit]

  def verifyCredential(userId: Long, password: String): Future[Boolean]

  def updateTelNumber(userId: Long, tel: String, token: String): Future[Unit]

  def getContactRequestList(userId: Long, offset: Int, limit: Int): Future[Seq[ContactRequest]]

  def sendValidationCode(action: OperationCode, userId: Option[Long], tel: String, countryCode: Option[Int] = None): Future[Unit]

  def checkValidationCode(valCode: String, action: OperationCode, tel: String, countryCode: Option[Int] = None): Future[Option[String]]

  def updateUserRoles(userId: Long, addRoles: Boolean, roles: Seq[Role]): Future[UserInfo]

  def getUsersByTelList(fields: Option[Seq[UserInfoProp]], tels: Seq[String]): Future[Seq[yunkai.UserInfo]]
}
