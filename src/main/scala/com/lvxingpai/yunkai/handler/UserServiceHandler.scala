package com.lvxingpai.yunkai.handler

import java.security.MessageDigest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{BooleanNode, LongNode, NullNode, TextNode}
import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.Implicits._
import com.lvxingpai.yunkai.model.{ChatGroup, Conversation, Credential, Sequence, UserInfo}
import com.lvxingpai.yunkai.{NotFoundException, UserInfoProp, Userservice, _}
import com.mongodb.DuplicateKeyException
import com.twitter.util.{Future, FuturePool}
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.{Query, UpdateOperations}

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.language.{implicitConversions, postfixOps}
import scala.util.Random

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
    UserServiceHandler.createChatGroup(creator, name, members, chatGroupProps) map (chatGroup => {
      if (chatGroup == null) throw new NotFoundException("Create chatGroup failure") else UserServiceHandler.chatGroupConversion(chatGroup)
    })
  }

  override def getChatGroup(chatGroupId: Long): Future[yunkai.ChatGroup] = {
    val result = UserServiceHandler.getChatGroup(chatGroupId)
    result map (item => {
      if (item == null) throw NotFoundException("Chat group not found") else UserServiceHandler.chatGroupConversion(item)
    })
  }

  override def updateChatGroup(chatGroupId: Long, chatGroupProps: Map[ChatGroupProp, String]): Future[yunkai.ChatGroup] = {
    val result = UserServiceHandler.updateChatGroup(chatGroupId, chatGroupProps)
    result map (item => {
      if (item == null) throw NotFoundException("Chat group not found") else UserServiceHandler.chatGroupConversion(item)
    })

  }

  override def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]]): Future[Seq[yunkai.ChatGroup]] = {
    val result = UserServiceHandler.getUserChatGroups(userId, fields)
    for {
      items <- result
    } yield {
      if (items isEmpty) throw NotFoundException(s"User $userId chat groups not found")
      else items map UserServiceHandler.chatGroupConversion
    }
  }

  override def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Unit] =
    UserServiceHandler.addChatGroupMembers(chatGroupId, userIds)

  override def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Unit] =
    UserServiceHandler.removeChatGroupMembers(chatGroupId, userIds)

  override def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]]): Future[Seq[yunkai.UserInfo]] = {
    val result = UserServiceHandler.getChatGroupMembers(chatGroupId, fields)
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

  // 产生salt
  def generateSalt(): String = {
    // 生成64个字节的salt
    val md5 = MessageDigest.getInstance("MD5")
    //使用指定的字节更新摘要
    md5.update(Random.nextLong().toString.getBytes)
    md5.digest().toString
  }

  // 产生密文
  def generatePassword(salt: String, password: String): String = {
    // 将密码与salt一起生成密文
    val msg = salt + password
    val bytes = MessageDigest.getInstance("SHA-256").digest(msg.getBytes)
    bytes map ("%02x".format(_)) mkString
  }

  def updatePassword(userId: Long, newPassword: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val query = ds.find(classOf[Credential], Credential.fdUserId, userId)
    if (query isEmpty) throw new NotFoundException(s"User userId=$userId credential is not found")
    else {
      val salt = generateSalt
      // 更新Credential
      val updateOps = ds.createUpdateOperations(classOf[Credential]).set(Credential.fdSalt, salt).set(Credential.fdPasswdHash, generatePassword(salt, newPassword))
      ds.updateFirst(query, updateOps)
    }

    // 触发重置用户密码的事件
    val eventArgs = scala.collection.immutable.Map(
      "userId" -> LongNode.valueOf(userId)
    )
    EventEmitter.emitEvent(EventEmitter.evtResetPassword, eventArgs)
  }

  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
    val userId = user.userId
    val nickName = user.nickName
    val avatar = Option(user.avatar)
    val gender = None
    val signature = Option(user.signature)
    val tel = Option(user.tel)

    yunkai.UserInfo(userId, nickName, avatar, gender, signature, tel)
  }

  // 取用户ID
  def populateId(sequenceType: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Long] = {
    futurePool {
      val query: Query[Sequence] = ds.createQuery(classOf[Sequence])
      query.field(Sequence.fdColumn).equal(sequenceType)
      val ops: UpdateOperations[Sequence] = ds.createUpdateOperations(classOf[Sequence]).inc(Sequence.fdCount)
      //查询或者修改异常, 参数4如果查找不存在, 则新建一个对象, 参数3表示返回新建的对象的count
      ds.findAndModify(query, ops, false, true).count
      //if (ret != null) ret.count else throw new NotFoundException(s"Sequence $sequenceType not found")
    }
  }

  //  // 创建讨论组
  def createChatGroup(creator: Long, name: String, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] = {
    val futureGid = populateId(Sequence.groupId)(ds, futurePool)
    // 如果讨论组创建人未选择其他的人，那么就创建者自己一个人，如果选择了其他人，那么群成员便是创建者和其他创建者拉进来的人
    val participants = (members :+ creator).toSet
    for {
      gid <- futureGid
    } yield {
      val cg = ChatGroup(creator, gid, name, participants.toSeq)
      chatGroupProps foreach (item => {
        item._1 match {
          case ChatGroupProp.GroupDesc => cg.groupDesc = item._2
          case ChatGroupProp.Avatar => cg.avatar = item._2
          case ChatGroupProp.GroupType => cg.groupType = item._2
          case _ => ""
        }
      })
      cg.admin = Seq(creator)
      cg.createTime = java.lang.System.currentTimeMillis()
      cg.updateTime = java.lang.System.currentTimeMillis()
      // 1. gid重复 2. 数据库通信异常  3. 切面
      try {
        ds.save[ChatGroup](cg)
        // 触发创建讨论组的事件
        val eventArgs = scala.collection.immutable.Map(
          "chatGroupId" -> LongNode.valueOf(cg.chatGroupId),
          "name" -> TextNode.valueOf(cg.name),
          "groupDesc" -> (if (cg.groupDesc != null && cg.groupDesc.nonEmpty) TextNode.valueOf(cg.groupDesc) else NullNode.getInstance()),
          "groupType" -> TextNode.valueOf(cg.groupType),
          "avatar" -> (if (cg.avatar != null && cg.avatar.nonEmpty) TextNode.valueOf(cg.avatar) else NullNode.getInstance()),
          "tags" -> (new ObjectMapper).valueToTree(cg.tags),
          "admin" -> (new ObjectMapper).valueToTree(cg.admin),
          "participants" -> (new ObjectMapper).valueToTree(cg.participants),
          "visible" -> BooleanNode.valueOf(cg.visible)
        )
        EventEmitter.emitEvent(EventEmitter.evtCreateChatGroup, eventArgs)
        cg
      } catch {
        case ex: DuplicateKeyException => throw new InvalidArgsException(s"Chat group $gid duplicated")
      }
    }
  }

  //  // 获取讨论组信息
  def getChatGroup(chatGroupId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] =
    futurePool {
      ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId).get()
    }

  implicit def chatGroupConversion(chatGroup: ChatGroup): yunkai.ChatGroup = {
    val chatGroupId = chatGroup.chatGroupId
    val name = chatGroup.name
    val groupDesc = chatGroup.groupDesc
    val tempGroupType = chatGroup.groupType
    val groupType = tempGroupType match {
      case "ChatGroup" => GroupType.ChatGroup
      case "Group" => GroupType.Group
      case _ => throw new NoSuchElementException(tempGroupType.toString)
    }
    val avatar = chatGroup.avatar
    val tags = chatGroup.tags
    val creator = chatGroup.creator
    val admin = chatGroup.admin
    val participants = chatGroup.participants
    val maxUsers = chatGroup.maxUsers
    val createTime = chatGroup.createTime
    val updateTime = chatGroup.updateTime
    val visible = chatGroup.visible

    yunkai.ChatGroup(chatGroupId, name, toOption(groupDesc), groupType, toOption(avatar), toOption(tags), creator, admin, participants, maxUsers, createTime, updateTime, visible)
  }

  def toOption[T](value: T): Option[T] = if (value != null) Some(value) else None

  // 修改讨论组信息（比如名称、描述等）
  def updateChatGroup(chatGroupId: Long, chatGroupProps: Map[ChatGroupProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] = futurePool {
    val query = ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId)
    if (query isEmpty) throw new NotFoundException(s"ChatGroup chatGroupId=$chatGroupId not found, update failure")
    else {
      val updateOps = chatGroupProps.foldLeft(ds.createUpdateOperations(classOf[ChatGroup]))((ops, entry) => {
        val (key, value) = entry
        key match {
          case ChatGroupProp.Name => ops.set(ChatGroup.fdName, value)
          case ChatGroupProp.GroupDesc => ops.set(ChatGroup.fdGroupDesc, value)
          case ChatGroupProp.Avatar => ops.set(ChatGroup.fdAvatar, value)
          case ChatGroupProp.Tags => ops.set(ChatGroup.fdTags, value)
          case ChatGroupProp.Visible => ops.set(ChatGroup.fdVisible, value)
          case _ => ops
        }
      })
      ds.updateFirst(query, updateOps)
    }
    val result = query.get()

    // 触发修改讨论组属性的事件
    val eventArgs = scala.collection.immutable.Map(
      "chatGroupId" -> LongNode.valueOf(result.chatGroupId),
      "name" -> TextNode.valueOf(result.name),
      "groupDesc" -> (if (result.groupDesc != null && result.groupDesc.nonEmpty) TextNode.valueOf(result.groupDesc) else NullNode.getInstance()),
      "avatar" -> (if (result.avatar != null && result.avatar.nonEmpty) TextNode.valueOf(result.avatar) else NullNode.getInstance()),
      "tags" -> (new ObjectMapper()).valueToTree(result.tags),
      "admin" -> (new ObjectMapper()).valueToTree(result.admin),
      "visible" -> BooleanNode.valueOf(result.visible)
    )
    EventEmitter.emitEvent(EventEmitter.evtModChatGroup, eventArgs)

    result
  }

  // 获取用户讨论组信息
  def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[ChatGroup]] = {
    // 从群组中遍历查找participants中是否含有用户，取出含有用户的groupId
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdParticipants).hasThisOne(userId)
    // 限定字段获取的范围
    val retrievedFields = fields.getOrElse(Seq()) map {
      case ChatGroupProp.ChatGroupId => ChatGroup.fdChatGroupId
      case ChatGroupProp.Name => ChatGroup.fdName
      case ChatGroupProp.Avatar => ChatGroup.fdAvatar
      case ChatGroupProp.Creator => ChatGroup.fdCreator
      case ChatGroupProp.GroupDesc => ChatGroup.fdGroupDesc
      case ChatGroupProp.GroupType => ChatGroup.fdGroupType
      case ChatGroupProp.MaxUsers => ChatGroup.fdMaxUsers
      case ChatGroupProp.Tags => ChatGroup.fdTags
      case ChatGroupProp.Visible => ChatGroup.fdVisible
      case _ => ""
    } filter (_ nonEmpty)

    if (retrievedFields nonEmpty)
      query.retrievedFields(true, retrievedFields :+ ChatGroup.fdChatGroupId: _*)
    futurePool {
      query.asList().toSeq.filter(_ != null)
    }
  }

  // 批量添加讨论组成员
  def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      // 更新讨论组的成员列表
      val queryChatGroup = ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId)
      val chatGroupUpdateOps = ds.createUpdateOperations(classOf[ChatGroup]).addAll(ChatGroup.fdParticipants, userIds, false)
      ds.updateFirst(queryChatGroup, chatGroupUpdateOps)

      // 更新Conversation的成员列表
      // 目前先这么做，后面给成观察者模式
      val queryConversation = ds.find(classOf[Conversation], Conversation.fdId, queryChatGroup.get().getId)
      val conversationUpdateOps = ds.createUpdateOperations(classOf[Conversation]).addAll(Conversation.fdParticipants, userIds, false)
      ds.updateFirst(queryConversation, conversationUpdateOps)

      val chatGroup = queryChatGroup.get
      // 触发添加讨论组成员的事件
      val eventArgs = scala.collection.immutable.Map(
        "chatGroupId" -> LongNode.valueOf(chatGroup.chatGroupId),
        "participants" -> (new ObjectMapper()).valueToTree(chatGroup.participants)
      )
      EventEmitter.emitEvent(EventEmitter.evtAddGroupMembers, eventArgs)

    }

  // 批量删除讨论组成员
  def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      // 更新讨论组的成员列表
      val queryChatGroup = ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId)
      val chatGroupUpdateOps = ds.createUpdateOperations(classOf[ChatGroup]).removeAll(ChatGroup.fdParticipants, userIds)
      ds.updateFirst(queryChatGroup, chatGroupUpdateOps)

      // 更新Conversation的成员列表
      // 目前先这么做，后面给成观察者模式
      val queryConversation = ds.find(classOf[Conversation], Conversation.fdId, queryChatGroup.get().getId)
      val conversationUpdateOps = ds.createUpdateOperations(classOf[Conversation]).removeAll(Conversation.fdParticipants, userIds)
      ds.updateFirst(queryConversation, conversationUpdateOps)

      val chatGroup = queryChatGroup.get
      // 触发添加/删除讨论组成员的事件
      val eventArgs = scala.collection.immutable.Map(
        "chatGroupId" -> LongNode.valueOf(chatGroup.chatGroupId),
        "participants" -> (new ObjectMapper()).valueToTree(chatGroup.participants)
      )
      EventEmitter.emitEvent(EventEmitter.evtRemoveGroupMembers, eventArgs)
    }

  // 获得讨论组成员
  def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[UserInfo]] =
    futurePool {
      val query = ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId).get().participants
      val queryUserInfo = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(query)
      val retrievedFields = fields.getOrElse(Seq()) map {
        case UserInfoProp.UserId => UserInfo.fdUserId
        case UserInfoProp.NickName => UserInfo.fdNickName
        case UserInfoProp.Avatar => UserInfo.fdAvatar
        case _ => ""
      } filter (_ nonEmpty)
      if (retrievedFields nonEmpty)
        queryUserInfo.retrievedFields(true, retrievedFields :+ UserInfo.fdUserId: _*)
      queryUserInfo.toList
    }
}
