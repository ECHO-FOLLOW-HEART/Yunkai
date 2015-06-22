package com.lvxingpai.yunkai.handler

import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.Implicits._
import com.lvxingpai.yunkai.model.{ ChatGroup, UserInfo }
import com.lvxingpai.yunkai.{ NotFoundException, UserInfoProp, Userservice, _ }
import com.twitter.util.Future

import scala.collection.Map
import scala.language.{ implicitConversions, postfixOps }

/**
 * 提供Yunkai服务
 *
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {

  import UserServiceHandler.userInfoConversion

  override def getUserById(userId: Long): Future[yunkai.UserInfo] = {

    AccountManager.getUserById(userId, fields = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar,
      UserInfoProp.Avatar, UserInfoProp.Tel, UserInfoProp.Signature)) map (userInfo => {
      if (userInfo nonEmpty)
        userInfo.get
      else
        throw NotFoundException(s"Cannot find user: $userId")
    })
  }

  override def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String]): Future[Unit] = {
    val updateData = userInfo map (entry => {
      val (propName, propStr) = entry

      val propVal = propName match {
        case UserInfoProp.UserId => propStr.toLong
        case UserInfoProp.Gender => propStr match {
          case "m" => Gender.Male
          case "f" => Gender.Female
          case null => null
          case _ => throw InvalidArgsException("Invalid gender")
        }
        case _ => if (propStr != null) propStr.trim else null
      }

      propName -> propVal
    })

    AccountManager.updateUserInfo(userId, updateData)
  }

  override def isContact(userA: Long, userB: Long): Future[Boolean] = AccountManager.isContact(userA, userB)

  override def addContact(userA: Long, userB: Long): Future[Unit] = AccountManager.addContact(userA, userB)

  override def addContacts(userA: Long, userB: Seq[Long]): Future[Unit] = AccountManager.addContact(userA, userB: _*)

  override def removeContact(userA: Long, userB: Long): Future[Unit] = AccountManager.removeContacts(userA, userB)

  override def removeContacts(userA: Long, userList: Seq[Long]): Future[Unit] =
    AccountManager.removeContacts(userA, userList: _*)

  override def getContactList(userId: Long, fields: Option[Seq[UserInfoProp]],
    offset: Option[Int], count: Option[Int]): Future[Seq[yunkai.UserInfo]] = {
    AccountManager.getContactList(userId, fields = fields.getOrElse(Seq())) map
      (_ map UserServiceHandler.userInfoConversion)
  }

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

  override def updatePassword(userId: Long, newPassword: String): Future[Unit] =
    AccountManager.updatePassword(userId, newPassword)

  override def createUser(nickName: String, password: String, tel: Option[String]): Future[yunkai.UserInfo] = {
    AccountManager.createUser(nickName, password, tel) map (userInfo => {
      if (userInfo == null)
        throw new NotFoundException("Create user failure")
      else
        UserServiceHandler.userInfoConversion(userInfo)
    })
  }

  override def createChatGroup(creator: Long, name: String, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, String]): Future[yunkai.ChatGroup] = {
    GroupManager.createChatGroup(creator, name, members, chatGroupProps) map (chatGroup => {
      if (chatGroup == null)
        throw new NotFoundException("Create chatGroup failure")
      else
        UserServiceHandler.chatGroupConversion(chatGroup)
    })
  }

  override def getChatGroup(chatGroupId: Long): Future[yunkai.ChatGroup] = {
    val result = GroupManager.getChatGroup(chatGroupId)
    result map (item => {
      if (item == null) throw NotFoundException("Chat group not found") else UserServiceHandler.chatGroupConversion(item)
    })
  }

  override def updateChatGroup(chatGroupId: Long, chatGroupProps: Map[ChatGroupProp, String]): Future[yunkai.ChatGroup] = {
    val result = GroupManager.updateChatGroup(chatGroupId, chatGroupProps)
    result map (item => {
      if (item == null) throw NotFoundException("Chat group not found") else UserServiceHandler.chatGroupConversion(item)
    })

  }

  override def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]]): Future[Seq[yunkai.ChatGroup]] = {
    val result = GroupManager.getUserChatGroups(userId, fields)
    for {
      items <- result
    } yield {
      if (items isEmpty) throw NotFoundException(s"User $userId chat groups not found")
      else items map UserServiceHandler.chatGroupConversion
    }
  }

  override def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Unit] =
    GroupManager.addChatGroupMembers(chatGroupId, userIds)

  override def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Unit] =
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

  override def add(val1: Int, val2: Int): Future[Int] = Future {
    val1 + val2
  }

  override def range(start: Int, end: Int, step: Option[Int]): Future[Seq[Int]] = Future {
    Range(start, end, step.getOrElse(1))
  }

  override def getMultipleUsers(userIdList: Seq[Long], fields: Seq[UserInfoProp]): Future[Map[Long, yunkai.UserInfo]] = {
    AccountManager.getUsersByIdList(true, fields, userIdList: _*) map (resultMap => {
      resultMap mapValues (value => (value map userInfoConversion).orNull)
    })
  }
}

object UserServiceHandler {

  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
    val userId = user.userId
    val nickName = user.nickName
    val avatar = Option(user.avatar)
    val gender = None
    val signature = Option(user.signature)
    val tel = Option(user.tel)

    yunkai.UserInfo(userId, nickName, avatar, gender, signature, tel)
  }

  implicit def chatGroupConversion(chatGroup: ChatGroup): yunkai.ChatGroup = {
    val chatGroupId = chatGroup.chatGroupId
    val name = chatGroup.name
    val groupDesc = chatGroup.groupDesc
    //    val tempGroupType = chatGroup.groupType
    //    val groupType = tempGroupType match {
    //      case "chatgroup" => GroupType.Chatgroup
    //      case "forum" => GroupType.Forum
    //      case _ => throw new NoSuchElementException(tempGroupType.toString)
    //    }
    val avatar = chatGroup.avatar
    val tags = chatGroup.tags
    val creator = chatGroup.creator
    val admin = chatGroup.admin
    val participants = chatGroup.participants
    val maxUsers = chatGroup.maxUsers
    val createTime = chatGroup.createTime
    val updateTime = chatGroup.updateTime
    val visible = chatGroup.visible

    yunkai.ChatGroup(chatGroupId, name, Option(groupDesc), Option(avatar), Option(tags),
      creator, admin, participants, maxUsers, createTime, updateTime, visible)
  }

}
