package com.lvxingpai.yunkai.handler

import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.model.ChatGroup
import com.lvxingpai.yunkai.{ NotFoundException, UserInfoProp, Userservice, _ }
import com.twitter.util.Future

import scala.collection
import scala.collection.JavaConversions._
import scala.language.{ implicitConversions, postfixOps }

/**
 * 提供Yunkai服务
 *
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {

  //  import UserServiceHandler.userInfoConversion

  val accountManager = Global.injector.getInstance(classOf[AccountManager])
  val groupManager = Global.injector.getInstance(classOf[GroupManager])

  override def getUserById(userId: Long, fields: Option[Seq[UserInfoProp]], selfId: Option[Long] = None): Future[yunkai.UserInfo] = {
    accountManager.getUserById(userId, fields.getOrElse(Seq()), selfId) map (userInfo => {
      if (userInfo nonEmpty)
        userInfo.get
      else
        throw NotFoundException(Some(s"Cannot find user: $userId"))
    })
  }

  override def updateUserInfo(userId: Long, userInfo: scala.collection.Map[UserInfoProp, String]): Future[yunkai.UserInfo] = {
    val updateData = Map(userInfo.toSeq map (entry => {
      val (propName, propStr) = entry
      val propVal = propName match {
        case UserInfoProp.UserId => propStr.toLong
        case _ => if (propStr != null) propStr.trim else null
      }
      propName -> propVal
    }): _*)
    accountManager.updateUserInfo(userId, updateData) map Implicits.YunkaiConversions.userConversion
  }

  override def isContact(userA: Long, userB: Long): Future[Boolean] = accountManager.isContact(userA, userB)

  override def addContact(userA: Long, userB: Long): Future[Unit] = accountManager.addContact(userA, userB)

  override def addContacts(userA: Long, userB: Seq[Long]): Future[Unit] = accountManager.addContact(userA, userB: _*)

  override def removeContact(userA: Long, userB: Long): Future[Unit] = accountManager.removeContacts(userA, userB)

  override def removeContacts(userA: Long, userList: Seq[Long]): Future[Unit] =
    accountManager.removeContacts(userA, userList: _*)

  override def getContactList(userId: Long, fields: Option[Seq[UserInfoProp]], offset: Option[Int],
    count: Option[Int]): Future[Seq[yunkai.UserInfo]] = {
    accountManager.getContactList(userId, fields = fields.getOrElse(Seq()), offset = offset, count = count)
  }

  override def updateMemo(userA: Long, userB: Long, memo: String): Future[Unit] = {
    accountManager.updateMemo(userA, userB, memo)
  }

  override def searchUserInfo(
    queryFields: scala.collection.Map[UserInfoProp, String],
    fields: Option[Seq[UserInfoProp]], offset: Option[Int], count: Option[Int]
  ): Future[Seq[yunkai.UserInfo]] =
    accountManager.searchUserInfo(Map(queryFields.toSeq: _*), fields, offset, count)

  override def getContactCount(userId: Long): Future[Int] = accountManager.getContactCount(userId)

  /**
   * 用户登录
   *
   * @param loginName 登录所需要的用户名
   * @param password  密码
   * @return 用户的详细资料
   */
  override def login(loginName: String, password: String, source: String): Future[yunkai.UserInfo] = {
    accountManager.login(loginName, password, source) map Implicits.YunkaiConversions.userConversion
  }

  override def resetPassword(userId: Long, oldPassword: String, newPassword: String): Future[Unit] =
    accountManager.resetPassword(userId, oldPassword, newPassword)

  override def resetPasswordByToken(userId: Long, newPassword: String, token: String): Future[Unit] =
    accountManager.resetPasswordByToken(userId, newPassword, token)

  override def createUser(nickName: String, password: String,
    miscInfo: Option[scala.collection.Map[UserInfoProp, String]]): Future[yunkai.UserInfo] = {
    val merged = (miscInfo getOrElse collection.Map[UserInfoProp, String]()) + (UserInfoProp.NickName -> nickName)
    accountManager.createUserPoly(password, Some(merged)) map Implicits.YunkaiConversions.userConversion
  }

  override def createUserPoly(regType: String, regName: String, password: String,
    miscInfo: Option[collection.Map[UserInfoProp, String]]): Future[yunkai.UserInfo] = {
    val regInfo = regType match {
      case t if t == UserInfoProp.Email.name.toLowerCase() => UserInfoProp.Email -> regName
      case t if t == UserInfoProp.Tel.name.toLowerCase() => UserInfoProp.Tel -> regName
    }
    accountManager.createUserPoly(password, Some((miscInfo getOrElse collection.Map()) + regInfo)) map
      Implicits.YunkaiConversions.userConversion
  }

  override def loginByOAuth(code: String, source: String): Future[yunkai.UserInfo] = {
    accountManager.loginByWeixin(code, source)
  }

  override def isBlocked(selfId: Long, targetId: Long): Future[Boolean] =
    accountManager.isBlocked(selfId, targetId)

  override def updateBlackList(userA: Long, userB: Long, block: Boolean): Future[Unit] =
    accountManager.updateBlackList(userA, userB, block)

  override def getChatGroups(groupIdList: Seq[Long] = Seq[Long](), fields: Option[Seq[ChatGroupProp]]): Future[Map[Long, yunkai.ChatGroup]] = {
    groupManager.getChatGroups(fields.getOrElse(Seq()), groupIdList: _*) map (resultMap => {
      resultMap mapValues (value => (value map UserServiceHandler.chatGroupConversion).orNull)
    })
  }

  override def getChatGroup(chatGroupId: Long, fields: Option[Seq[ChatGroupProp]]): Future[yunkai.ChatGroup] = {
    groupManager.getChatGroup(chatGroupId, fields.getOrElse(Seq())) map (item => {
      if (item isEmpty)
        throw NotFoundException(Some("Chat group not found"))
      else
        UserServiceHandler.chatGroupConversion(item.get)
    })
  }

  override def updateChatGroup(chatGroupId: Long, operatorId: Long, chatGroupProps: scala.collection.Map[ChatGroupProp, String]): Future[yunkai.ChatGroup] = {
    val updateInfo = Map(chatGroupProps.toSeq map (entry => {
      val (prop, value) = entry
      prop -> (prop match {
        case ChatGroupProp.Name | ChatGroupProp.GroupDesc | ChatGroupProp.Avatar => value.trim
        case ChatGroupProp.MaxUsers => value.toInt
        case ChatGroupProp.Visible => value.toBoolean
        case _ => null
      })
    }): _*) filter (_._2 != null)

    groupManager.updateChatGroup(chatGroupId, operatorId, updateInfo) map (item => {
      if (item isEmpty)
        throw NotFoundException(Some("Chat group not found"))
      else
        UserServiceHandler.chatGroupConversion(item.get)
    })
  }

  override def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]], offset: Option[Int],
    count: Option[Int]): Future[Seq[yunkai.ChatGroup]] = {
    val result = groupManager.getUserChatGroups(userId, fields.getOrElse(Seq()))
    for {
      items <- result
    } yield {
      if (items isEmpty) throw NotFoundException(Some(s"User $userId chat groups not found"))
      else items map UserServiceHandler.chatGroupConversion
    }
  }

  override def addChatGroupMembers(chatGroupId: Long, operatorId: Long, userIds: Seq[Long]): Future[Seq[Long]] =
    groupManager.addChatGroupMembers(chatGroupId, operatorId, userIds)

  override def removeChatGroupMembers(chatGroupId: Long, operatorId: Long, userIds: Seq[Long]): Future[Seq[Long]] =
    groupManager.removeChatGroupMembers(chatGroupId, operatorId, userIds)

  override def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]], selfId: Option[Long]): Future[Seq[yunkai.UserInfo]] = {
    groupManager.getChatGroupMembers(chatGroupId, fields, selfId)
  }

  override def getUsersById(userIdList: Seq[Long] = Seq[Long](), fields: Option[Seq[UserInfoProp]], selfId: Option[Long]): Future[Map[Long, yunkai.UserInfo]] = {
    accountManager.getUsersByIdList(fields.getOrElse(Seq()), selfId, userIdList: _*) map (resultMap => {
      resultMap mapValues (_.orNull)
    })
  }

  override def createChatGroup(creator: Long, participants: Seq[Long],
    chatGroupProps: Option[scala.collection.Map[ChatGroupProp, String]]): Future[yunkai.ChatGroup] = {
    // 处理额外信息
    val miscInfo = Map(chatGroupProps.getOrElse(scala.collection.Map()).toSeq map (entry => {
      val prop = entry._1
      val value = entry._2
      val value2 = prop match {
        case ChatGroupProp.Name => value
        case ChatGroupProp.GroupDesc => value
        case ChatGroupProp.Avatar => value
        case ChatGroupProp.MaxUsers => value.toInt
        case ChatGroupProp.Visible => value.toBoolean
        case _ => null
      }
      prop -> value2
    }): _*) filter (_._2 != null)

    groupManager.createChatGroup(creator, participants, miscInfo) map UserServiceHandler.chatGroupConversion
  }

  override def getUserChatGroupCount(userId: Long): Future[Int] = groupManager.getUserChatGroupCount(userId)

  override def sendContactRequest(sender: Long, receiver: Long, message: Option[String]): Future[String] =
    accountManager.sendContactRequest(sender, receiver, message) map (_.toString)

  override def rejectContactRequest(requestId: String, message: Option[String]): Future[Unit] =
    accountManager.rejectContactRequest(requestId, message)

  override def acceptContactRequest(requestId: String): Future[Unit] =
    accountManager.acceptContactRequest(requestId)

  override def cancelContactRequest(requestId: String): Future[Unit] =
    accountManager.cancelContactRequest(requestId)

  override def verifyCredential(userId: Long, password: String): Future[Boolean] =
    accountManager.verifyCredential(userId, password)

  override def updateTelNumber(userId: Long, tel: String, token: String): Future[Unit] = accountManager.updateTelNumber(userId, tel, token)

  override def getContactRequests(userId: Long, offset: Option[Int], limit: Option[Int]): Future[Seq[ContactRequest]] = {
    import com.lvxingpai.yunkai.Implicits.YunkaiConversions._
    import com.lvxingpai.yunkai.{ ContactRequest => YunkaiContactRequest }

    val defaultOffset = 0
    val defaultCount = 50
    val maxCount = 100

    val actualOffset = offset getOrElse defaultOffset
    val actualLimit = Math.max(offset getOrElse defaultCount, maxCount)

    accountManager.getContactRequestList(userId, actualOffset, actualLimit) map (list => {
      list map (v => v: YunkaiContactRequest)
    })
  }

  override def sendValidationCode(action: OperationCode, userId: Option[Long], tel: String, countryCode: Option[Int]): Future[Unit] =
    accountManager.sendValidationCode(action, userId, tel, countryCode)

  override def checkValidationCode(code: String, action: OperationCode, tel: String, countryCode: Option[Int]): Future[String] = {
    accountManager.checkValidationCode(code, action, tel, countryCode) map (opt => {
      opt getOrElse {
        throw ValidationCodeException()
      }
    })
  }

  override def updateUserRoles(userId: Long, addRoles: Boolean, roles: Option[Seq[Role]]): Future[yunkai.UserInfo] =
    accountManager.updateUserRoles(userId, addRoles, roles getOrElse Seq()) map
      Implicits.YunkaiConversions.userConversion

  override def isMember(userId: Long, chatGroupId: Long): Future[Boolean] =
    groupManager.isMember(userId, chatGroupId)

  override def getUsersByTelList(fields: Option[Seq[UserInfoProp]], tels: Seq[String]): Future[Seq[yunkai.UserInfo]] = accountManager.getUsersByTelList(fields, tels)

  override def ping(): Future[String] = Future("pong")

  override def getUserSecretKey(userId: Long): Future[String] = {
    accountManager.getSecretKey(userId) map (result => {
      if (result.nonEmpty) result.get.key
      else throw NotFoundException(Some(s"Unable to get secret key relating to user $userId"))
    })
  }
}

object UserServiceHandler {

  //  /**
  //   * 在models.UserInfo和yunkai.UserInfo之间进行类型转换
  //   *
  //   * @param user
  //   * @return
  //   */
  //  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
  //    val userId = user.userId
  //    val id = user.id
  //    val nickName = user.nickName
  //    val avatar = Option(user.avatar)
  //    val gender = Option(user.gender match {
  //      case "m" | "M" => Gender.Male
  //      case "f" | "F" => Gender.Female
  //      case "s" | "S" => Gender.Secret
  //      case "b" | "B" => Gender.Both
  //      case "u" | "U" | null => null
  //      case _ => throw new IllegalArgumentException("Invalid gender")
  //    })
  //    val signature = Option(user.signature)
  //    val tel = Option(user.tel)
  //    val loginStatus = user.loginStatus
  //    val loginTime = Option(user.loginTime)
  //    val logoutTime = Option(user.logoutTime)
  //    val loginSource = Option(user.loginSource) map (_.toSeq) getOrElse Seq()
  //
  //    val roles = Option(user.roles) map (_.toSeq map Role.apply) getOrElse Seq()
  //    yunkai.UserInfo(id.toString, userId, nickName, avatar, gender, signature, tel, loginStatus, loginTime, logoutTime, loginSource, roles = roles)
  //  }

  implicit def chatGroupConversion(chatGroup: ChatGroup): yunkai.ChatGroup = {
    val id = chatGroup.id
    val chatGroupId = chatGroup.chatGroupId
    val name = chatGroup.name
    val groupDesc = chatGroup.groupDesc
    val avatar = chatGroup.avatar
    val tags = Option(chatGroup.tags) map (_.toSeq)
    val creator = chatGroup.creator
    val admin = Option(chatGroup.admin) map (_.toSeq) getOrElse Seq()
    val participants = Option(chatGroup.participants) map (_.toSeq) getOrElse Seq()
    val maxUsers = chatGroup.maxUsers
    val createTime = chatGroup.createTime
    val updateTime = chatGroup.updateTime
    val visible = chatGroup.visible

    yunkai.ChatGroup(id.toString, chatGroupId, name, Option(groupDesc), Option(avatar), tags, creator, admin,
      participants, maxUsers, createTime, updateTime, visible)
  }

}
