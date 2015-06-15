package com.aizou.yunkai.handler

import java.security.MessageDigest

import com.aizou.yunkai
import com.aizou.yunkai.Implicits._
import com.aizou.yunkai.model.{ ChatGroup, Conversation, Credential, Relationship, Sequence, UserInfo }
import com.aizou.yunkai.{ AuthException, NotFoundException, UserInfoProp, Userservice, _ }
import com.fasterxml.jackson.databind.node.{ LongNode, NullNode, TextNode }
import com.mongodb.DuplicateKeyException
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.{ CriteriaContainer, Query, UpdateOperations }

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.util.Random

import scala.language.postfixOps
import scala.language.implicitConversions

/**
 * 提供Yunkai服务
 *
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {

  override def getUserById(userId: Long): Future[yunkai.UserInfo] = {
    UserServiceHandler.getUserById(userId) map (userInfo => {
      if (userInfo == null) throw new NotFoundException(s"User not found for userId=$userId")
      else {
        UserServiceHandler.userInfoConversion(userInfo)
      }
    })
  }

  override def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String]): Future[Unit] =
    UserServiceHandler.updateUserInfo(userId, userInfo)

  override def isContact(userA: Long, userB: Long): Future[Boolean] = UserServiceHandler.isContact(userA, userB)

  override def addContact(userA: Long, userB: Long): Future[Unit] = UserServiceHandler.addContact(userA, userB)

  override def addContacts(userA: Long, userB: Seq[Long]): Future[Unit] =
    UserServiceHandler.addContact(userA, userB: _*)

  override def removeContact(userA: Long, userB: Long): Future[Unit] = UserServiceHandler.removeContacts(userA, userB)

  override def removeContacts(userA: Long, userList: Seq[Long]): Future[Unit] =
    UserServiceHandler.removeContacts(userA, userList: _*)

  override def getContactList(userId: Long, fields: Option[Seq[UserInfoProp]],
    offset: Option[Int], count: Option[Int]): Future[Seq[yunkai.UserInfo]] = {
    UserServiceHandler.getContactList(userId, fields, offset, count) map (_ map UserServiceHandler.userInfoConversion)
  }

  /**
   * 用户登录
   *
   * @param loginName 登录所需要的用户名
   * @param password  密码
   * @return 用户的详细资料
   */
  override def login(loginName: String, password: String): Future[yunkai.UserInfo] =
    UserServiceHandler.login(loginName, password) map UserServiceHandler.userInfoConversion

  override def createUser(nickName: String, password: String, tel: Option[String]): Future[yunkai.UserInfo] = {
    UserServiceHandler.createUser(nickName, password, tel) map (userInfo => {
      if (userInfo == null) throw new NotFoundException("Create user failure") else UserServiceHandler.userInfoConversion(userInfo)
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
}

object UserServiceHandler {
  def getUserById(userId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] =
    futurePool {
      ds.find(classOf[UserInfo], "userId", userId).get()
    }

  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val query = ds.find(classOf[UserInfo], "userId", userId)
    if (query isEmpty) throw new NotFoundException(s"User userId=$userId is not found")
    val updateOps = userInfo.foldLeft(ds.createUpdateOperations(classOf[UserInfo]))((ops, entry) => {
      val (key, value) = entry
      key match {
        case UserInfoProp.NickName => ops.set(UserInfo.fdNickName, value)
        case UserInfoProp.Signature => ops.set(UserInfo.fdSignature, value)
        case _ => ops
      }
    })

    ds.updateFirst(query, updateOps)
  }

  def isContact(userA: Long, userB: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Boolean] =
    futurePool {
      val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
      ds.createQuery(classOf[Relationship]).field("userA").equal(user1).field("userB").equal(user2).get() != null
    }

  def addContact(userA: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      val cls = classOf[Relationship]

      for (userB <- targetUsers) {
        val (user1, user2) = if (userA <= userB) (userA, userB) else (userB, userA)
        val op = ds.createUpdateOperations(cls).set("userA", user1).set("userB", user2)
        val query = ds.createQuery(cls).field("userA").equal(user1).field("userB").equal(user2)
        ds.updateFirst(query, op, true)
      }
    }

  def removeContacts(userA: Long, targetUsers: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      def buildQuery(user1: Long, user2: Long): CriteriaContainer = {
        val l = Seq(user1, user2).sorted
        ds.createQuery(classOf[Relationship]).criteria("userA").equal(l head).criteria("userB").equal(l last)
      }

      val query = ds.createQuery(classOf[Relationship])
      query.or(targetUsers map (buildQuery(userA, _)): _*)
      ds.delete(query)
    }

  def getContactList(userId: Long, fields: Option[Seq[UserInfoProp]] = None, offset: Option[Int] = None,
    count: Option[Int] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[UserInfo]] = {
    val criteria = Seq("userA", "userB") map (f => ds.createQuery(classOf[Relationship]).criteria(f).equal(userId))
    val queryRel = ds.createQuery(classOf[Relationship])
    queryRel.or(criteria: _*)
    val defaultOffset = 0
    val defaultCount = 1000

    // 获得好友的userId
    val contactIds = futurePool {
      queryRel.offset(offset.getOrElse(defaultOffset)).limit(count.getOrElse(defaultCount))
      val result = for {
        rel <- queryRel.asList().toSeq
        filteredUser <- Seq(rel.getUserA, rel.getUserB) filter (_ != userId)
      } yield filteredUser
      result.toSeq
    }

    // 根据userId，批量获得好友信息
    def getUsersByIdList(fields: Option[Seq[UserInfoProp]], userIds: Long*): Future[Map[Long, Option[UserInfo]]] = {
      val query = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(userIds)

      // 限定字段获取的范围
      val retrievedFields = fields.getOrElse(Seq()) map {
        case UserInfoProp.UserId => UserInfo.fdUserId
        case UserInfoProp.NickName => UserInfo.fdNickName
        case UserInfoProp.Avatar => UserInfo.fdAvatar
        case _ => ""
      } filter (_ nonEmpty)

      if (retrievedFields nonEmpty)
        query.retrievedFields(true, retrievedFields :+ UserInfo.fdUserId: _*)

      futurePool {
        val result = (for {
          user <- query.asList().toSeq.filter(_ != null)
        } yield user.userId -> Some(user)) filter (_._1 != -1)

        Map(result: _*)
      }
    }

    for {
      cid <- contactIds
      userInfoMap <- getUsersByIdList(fields, cid: _*)
    } yield userInfoMap.toSeq.filter(_._2.nonEmpty).map(_._2.get)
  }

  def login(loginName: String, password: String)(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] = {
    // 获得用户信息
    val userInfo = futurePool {
      ds.find(classOf[UserInfo], UserInfo.fdTel, loginName).get()
    }

    // 获得机密信息
    val complex = userInfo map (v => {
      if (v != null) {
        val userId = v.userId
        (v, ds.find(classOf[Credential], Credential.fdUserId, userId).get())
      } else throw new AuthException("")
    })

    // 验证
    val result = complex map (v => {
      val (user, credential) = v
      val salt = credential.salt
      val encrypted = credential.passwdHash
      val msg = salt + password
      val bytes = MessageDigest.getInstance("SHA-256").digest(msg.getBytes)
      val digest = bytes map ("%02x".format(_)) mkString

      if (digest == encrypted) user
      else throw new AuthException("")
    })

    // 触发登录事件
    result map (v => {
      val eventArgs = scala.collection.immutable.Map(
        "userId" -> LongNode.valueOf(v.userId),
        "nickName" -> TextNode.valueOf(v.nickName),
        "avatar" -> (if (v.avatar != null && v.avatar.nonEmpty) TextNode.valueOf(v.avatar) else NullNode.getInstance())
      )
      EventEmitter.emitEvent(EventEmitter.evtLogin, eventArgs)
      v
    })
  }

  implicit def userInfoConversion(user: UserInfo): yunkai.UserInfo = {
    val userId = user.userId
    val nickName = user.nickName
    val avatar = if (user.avatar == null) None else Some(user.avatar)
    val gender = None
    val signature = None
    val tel = None

    yunkai.UserInfo(userId, nickName, avatar, gender, signature, tel)
  }

  // 新用户注册
  def createUser(nickName: String, password: String, tel: Option[String])(implicit ds: Datastore, futurePool: FuturePool): Future[UserInfo] = {
    // 取得用户ID
    val newUserId = populateId(Sequence.userId)(ds, futurePool)
    val tempTel: String = tel.orNull
    // 创建用户并保存
    val userInfo = for {
      userId <- newUserId
    } yield {
      val newUser = UserInfo(userId, nickName)
      newUser.tel = tempTel
      try {
        ds.save[UserInfo](newUser)
        newUser
      } catch {
        case ex: DuplicateKeyException => throw new InvalidArgsException(s"User $userId is existed")
      }
    }
    // 生成64个字节的salt
    val md5 = MessageDigest.getInstance("MD5")
    //使用指定的字节更新摘要
    md5.update(Random.nextLong().toString.getBytes)
    val salt = md5.digest().toString

    // 将密码与salt一起生成密文
    val msg = salt + password
    val bytes = MessageDigest.getInstance("SHA-256").digest(msg.getBytes)
    val digest = bytes map ("%02x".format(_)) mkString

    // 创建并保存新用户Credential实例
    for {
      userId <- newUserId
    } yield {
      val credential = Credential(userId, salt, digest)
      try {
        ds.save[Credential](credential)
      } catch {
        case ex: DuplicateKeyException => throw new InvalidArgsException(s"User $userId credential is existed")
      }
    }

    // 触发创建新用户的事件
    userInfo map (v => {
      val eventArgs = scala.collection.immutable.Map(
        "userId" -> LongNode.valueOf(v.userId),
        "nickName" -> TextNode.valueOf(v.nickName),
        "avatar" -> (if (v.avatar != null && v.avatar.nonEmpty) TextNode.valueOf(v.avatar) else NullNode.getInstance())
      )
      EventEmitter.emitEvent(EventEmitter.evtCreateUser, eventArgs)
    })

    userInfo
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
        cg
      } catch {
        case ex: DuplicateKeyException => throw new InvalidArgsException(s"Chat group $gid duplicated")
      }
    }
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
    query.get()
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
