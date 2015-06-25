package com.lvxingpai.yunkai.handler

import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.Implicits._
import com.lvxingpai.yunkai.model.{ChatGroup, UserInfo}
import com.lvxingpai.yunkai.{NotFoundException, UserInfoProp, Userservice, _}
import com.twitter.util.Future

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.language.{implicitConversions, postfixOps}

/**
 * 提供Yunkai服务
 *
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {

  import UserServiceHandler.userInfoConversion

  override def getUserById(userId: Long, fields: Option[Seq[UserInfoProp]]): Future[yunkai.UserInfo] = {
    AccountManager.getUserById(userId, fields.getOrElse(Seq())) map (userInfo => {
      if (userInfo nonEmpty)
        userInfo.get
      else
        throw NotFoundException(s"Cannot find user: $userId")
    })
  }

  override def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String]): Future[yunkai.UserInfo] = {
    val updateData = userInfo map (entry => {
      val (propName, propStr) = entry
      val propVal = propName match {
        case UserInfoProp.UserId => propStr.toLong
        case _ => if (propStr != null) propStr.trim else null
      }
      propName -> propVal
    })
    AccountManager.updateUserInfo(userId, updateData) map UserServiceHandler.userInfoConversion
  }

  override def isContact(userA: Long, userB: Long): Future[Boolean] = AccountManager.isContact(userA, userB)

  override def addContact(userA: Long, userB: Long): Future[Unit] = AccountManager.addContact(userA, userB)

  override def addContacts(userA: Long, userB: Seq[Long]): Future[Unit] = AccountManager.addContact(userA, userB: _*)

  override def removeContact(userA: Long, userB: Long): Future[Unit] = AccountManager.removeContacts(userA, userB)

  override def removeContacts(userA: Long, userList: Seq[Long]): Future[Unit] =
    AccountManager.removeContacts(userA, userList: _*)

  override def getContactList(userId: Long, fields: Option[Seq[UserInfoProp]], offset: Option[Int],
                              count: Option[Int]): Future[Seq[yunkai.UserInfo]] = {
    AccountManager.getContactList(userId, fields = fields.getOrElse(Seq()), offset = offset, count = count) map
      (_ map UserServiceHandler.userInfoConversion)
  }

  override def getContactCount(userId: Long): Future[Int] = AccountManager.getContactCount(userId)

  /**
   * 用户登录
   *
   * @param loginName 登录所需要的用户名
   * @param password  密码
   * @return 用户的详细资料
   */
  override def login(loginName: String, password: String): Future[yunkai.UserInfo] = {
    AccountManager.login(loginName, password) map UserServiceHandler.userInfoConversion
  }

  override def resetPassword(userId: Long, oldPassword: String, newPassword: String): Future[Unit] =
    AccountManager.updatePassword(userId, newPassword)

  override def createUser(nickName: String, password: String, miscInfo: Option[Map[UserInfoProp, String]]): Future[yunkai.UserInfo] = {
    val tel = miscInfo.getOrElse(Map()).get(UserInfoProp.Tel)
    AccountManager.createUser(nickName, password, tel) map (userInfo => {
      if (userInfo == null)
        throw new NotFoundException("Create user failure")
      else
        UserServiceHandler.userInfoConversion(userInfo)
    })
  }

  override def getChatGroups(groupIdList: Seq[Long] = Seq[Long](), fields: Option[Seq[ChatGroupProp]]): Future[Map[Long, yunkai.ChatGroup]] = {
    // TODO To be implemented
    throw new NotImplementedError()
  }

  override def getChatGroup(chatGroupId: Long, fields: Option[Seq[ChatGroupProp]]): Future[yunkai.ChatGroup] = {
    GroupManager.getChatGroup(chatGroupId, fields.getOrElse(Seq())) map (item => {
      if (item isEmpty)
        throw NotFoundException("Chat group not found")
      else
        UserServiceHandler.chatGroupConversion(item.get)
    })
  }

  override def updateChatGroup(chatGroupId: Long, chatGroupProps: Map[ChatGroupProp, String]): Future[yunkai.ChatGroup] = {
    val updateInfo = chatGroupProps map (entry => {
      val (prop, value) = entry
      prop -> (prop match {
        case ChatGroupProp.Name | ChatGroupProp.GroupDesc | ChatGroupProp.Avatar => value.trim
        case ChatGroupProp.MaxUsers => value.toInt
        case ChatGroupProp.Visible => value.toBoolean
        case _ => null
      })
    }) filter (_._2 != null)

    GroupManager.updateChatGroup(chatGroupId, updateInfo) map (item => {
      if (item isEmpty)
        throw NotFoundException("Chat group not found")
      else
        UserServiceHandler.chatGroupConversion(item.get)
    })
  }

  override def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]], offset: Option[Int],
                                 count: Option[Int]): Future[Seq[yunkai.ChatGroup]] = {
    val result = GroupManager.getUserChatGroups(userId, fields.getOrElse(Seq()))
    for {
      items <- result
    } yield {
      if (items isEmpty) throw NotFoundException(s"User $userId chat groups not found")
      else items map UserServiceHandler.chatGroupConversion
    }
  }

  override def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Seq[Long]] =
    GroupManager.addChatGroupMembers(chatGroupId, userIds)

  override def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Seq[Long]] =
    GroupManager.removeChatGroupMembers(chatGroupId, userIds)

  override def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]]): Future[Seq[yunkai.UserInfo]] = {
    val result = GroupManager.getChatGroupMembers(chatGroupId, fields)
    for {
      items <- result
    } yield {
      if (items isEmpty) throw new NotFoundException(s"Chat group $chatGroupId members not found")
      else items map UserServiceHandler.userInfoConversion
    }
  }

  override def getUsersById(userIdList: Seq[Long] = Seq[Long](), fields: Option[Seq[UserInfoProp]]): Future[Map[Long, yunkai.UserInfo]] = {
    AccountManager.getUsersByIdList(fields.getOrElse(Seq()), userIdList: _*) map (resultMap => {
      resultMap mapValues (value => (value map userInfoConversion).orNull)
    })
  }

  override def createChatGroup(creator: Long, participants: Seq[Long], chatGroupProps: Option[Map[ChatGroupProp, String]]): Future[yunkai.ChatGroup] = {
    // 处理额外信息
    val miscInfo = chatGroupProps.getOrElse(Map()) map (entry => {
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
    }) filter (_._2 != null)

    GroupManager.createChatGroup(creator, participants, miscInfo) map UserServiceHandler.chatGroupConversion
  }

  override def getUserChatGroupCount(userId: Long): Future[Int] = GroupManager.getUserChatGroupCount(userId)
}

object UserServiceHandler {

  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
    val userId = user.userId
    val nickName = user.nickName
    val avatar = Option(user.avatar)
    val gender = Option(user.gender match {
      case "m" | "M" => Gender.Male
      case "f" | "F" => Gender.Female
      case "s" | "S" => Gender.Secret
      case "u" | "U" | null => null
      case _ => throw new IllegalArgumentException("Invalid gender")
    })
    val signature = Option(user.signature)
    val tel = Option(user.tel)

    yunkai.UserInfo(userId, nickName, avatar, gender, signature, tel)
  }

  implicit def chatGroupConversion(chatGroup: ChatGroup): yunkai.ChatGroup = {
    val chatGroupId = chatGroup.chatGroupId
    val name = chatGroup.name
    val groupDesc = chatGroup.groupDesc
    val avatar = chatGroup.avatar
    val tags = Option(chatGroup.tags) map (_.toSeq)
    val creator = chatGroup.creator
    val admin = Option(chatGroup.admin) map (_.toSeq) getOrElse Seq()
    val participants = chatGroup.participants
    val maxUsers = chatGroup.maxUsers
    val createTime = chatGroup.createTime
    val updateTime = chatGroup.updateTime
    val visible = chatGroup.visible

    yunkai.ChatGroup(chatGroupId, name, Option(groupDesc), Option(avatar), tags,
      creator, admin, participants, maxUsers, createTime, updateTime, visible)
  }

}
