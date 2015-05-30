package com.aizou.yunkai.handler

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

import com.aizou.yunkai
import com.aizou.yunkai.Implicits._
import com.aizou.yunkai.model.{ Credential, Relationship, UserInfo }
import com.aizou.yunkai.{ AuthException, NotFoundException, UserInfoProp, Userservice }
import com.twitter.util.{ Future, FuturePool }
import com.aizou.yunkai.model.{ Credential, Relationship, UserInfo, ChatGroup, Sequence, Conversation }
import com.aizou.yunkai._
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.{ UpdateOperations, Query, CriteriaContainer }
import com.aizou.yunkai.util.Constant
import scala.collection.JavaConversions._
import scala.collection.Map
import scala.util.Random

/**
 * Created by zephyre on 5/4/15.
 */
class UserServiceHandler extends Userservice.FutureIface {

  override def getUserById(userId: Long): Future[yunkai.UserInfo] = {
    UserServiceHandler.getUserById(userId) map (userInfo => {
      if (userInfo isEmpty) throw new NotFoundException(s"User not found for userId=$userId") else userInfo.get
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
    offset: Option[Int], count: Option[Int]): Future[Seq[yunkai.UserInfo]] =
    UserServiceHandler.getContactList(userId, fields, offset, count) map (_ map UserServiceHandler.userInfoConversion)

  /**
   * 用户登录
   *
   * @param loginName 登录所需要的用户名
   * @param password  密码
   * @return 用户的详细资料
   */
  override def login(loginName: String, password: String): Future[yunkai.UserInfo] =
    UserServiceHandler.login(loginName, password) map UserServiceHandler.userInfoConversion

  override def createChatGroup(creator: Long, name: String, chatGroup: Map[ChatGroupProp, String], members: Seq[Long]): Future[Unit] =
    UserServiceHandler.createChatGroup(creator, name, chatGroup, members)

  override def getChatGroup(chatGroupId: Long): Future[yunkai.ChatGroup] = {
    val result = UserServiceHandler.getChatGroup(chatGroupId)
    result map (item => {
      if (item isEmpty) throw NotFoundException("Chat group not found") else item.get
    })
  }

  override def updateChatGroup(chatGroupId: Long, chatGroup: Map[ChatGroupProp, String]): Future[Unit] =
    UserServiceHandler.updateChatGroup(chatGroupId, chatGroup)

  override def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]]): Future[Seq[yunkai.ChatGroup]] = {
    val result = UserServiceHandler.getUserChatGroups(userId, fields)
    result map (item => {
      if (item isEmpty) throw NotFoundException(s"User $userId chat groups not found") else item
    })
  }

  override def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Unit] =
    UserServiceHandler.addChatGroupMembers(chatGroupId, userIds)

  override def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long]): Future[Unit] =
    UserServiceHandler.removeChatGroupMembers(chatGroupId, userIds)

  override def createUser(nickName: String, password: String, tel: String): Future[Unit] =
    UserServiceHandler.createUser(nickName, password, tel)

  override def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]]): Future[Seq[yunkai.UserInfo]] = {
    val result = UserServiceHandler.getChatGroupMembers(chatGroupId, fields)
    result map (item => {
      if (item isEmpty) throw new NotFoundException(s"Chat group $chatGroupId members not found") else item
    })
  }
}

object UserServiceHandler {
  def toOption[T](value: T): Option[T] = if (value != null) Some(value) else None

  def getUserById(userId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Option[yunkai.UserInfo]] =
    futurePool {
      val entry = ds.find(classOf[UserInfo], "userId", userId).get()
      if (entry == null) None
      else {
        Some(yunkai.UserInfo(entry.userId, entry.nickName, toOption(entry.avatar), None, None, None))
      }
    }

  def updateUserInfo(userId: Long, userInfo: Map[UserInfoProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val query = ds.find(classOf[UserInfo], "userId", userId)
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
    complex map (v => {
      val (user, credential) = v
      val salt = credential.salt
      val encrypted = credential.passwdHash
      val msg = salt + password
      val bytes = MessageDigest.getInstance("SHA-256").digest(msg.getBytes)
      val digest = bytes map ("%02x".format(_)) mkString

      if (digest == encrypted) user
      else throw new AuthException("")
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

  // 取用户ID
  def populateUserId()(implicit ds: Datastore, futurePool: FuturePool): Future[Long] = {
    futurePool {
      val query: Query[Sequence] = ds.createQuery(classOf[Sequence])
      query.field("column").equal(Sequence.userId)
      val ops: UpdateOperations[Sequence] = ds.createUpdateOperations(classOf[Sequence]).inc("count")
      //查询或者修改异常
      val ret: Sequence = ds.findAndModify(query, ops)
      if (ret != null) ret.count else throw new NotFoundException(s"Sequence not found")
    }
  }

  //  // 新用户注册
  //  UserInfo createUser(1:string nickName, 2:string password, 3:string tel) throws (1: InvalidArgsException ex)
  // 密码
  def createUser(nickName: String, password: String, tel: String)(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = {
    // 取得用户ID
    val newUserId = populateUserId()(ds, futurePool)
    // 创建用户并保存
    futurePool {
      for {
        userId <- newUserId
      } yield userId -> {
        val newUser = UserInfo(userId, nickName, tel)
        if (newUser != null) ds.save[UserInfo](newUser) else throw new NotFoundException("Create user instance failure")
      }
    }
    // 生成64个字节的salt
    val str = "" + Random.nextInt()
    val byteStr = str.getBytes()
    var md5: MessageDigest = null
    try {
      md5 = MessageDigest.getInstance("MD5")
    } catch {
      case ex: NoSuchAlgorithmException => throw new NotFoundException("No Such AlgorithmException for MD5")
    }

    //使用指定的字节更新摘要
    md5.update(byteStr)
    val salt = md5.digest().toString()

    // 将密码与salt一起生成密文
    val msg = salt + password
    var bytes: MessageDigest = null
    try {
      bytes = MessageDigest.getInstance("SHA-256")
    } catch {
      case ex: NoSuchAlgorithmException => throw new NotFoundException("No Such AlgorithmException for SHA-256")
    }
    val bytes1 = bytes.digest(msg.getBytes)
    val digest = bytes1 map ("%02x".format(_)) mkString

    // 创建并保存新用户Credential实例
    val result = for {
      userId <- newUserId
    } yield {
      val credential = Credential(userId, salt, digest)
      if (credential != null) ds.save[Credential](credential) else throw new NotFoundException("Create credential instance failure")
      ()
    }
    result
  }

  def populateGroupId()(implicit ds: Datastore, futurePool: FuturePool): Future[Long] = {
    futurePool {
      val query: Query[Sequence] = ds.createQuery(classOf[Sequence])
      query.field("column").equal(Sequence.groupId)
      val ops: UpdateOperations[Sequence] = ds.createUpdateOperations(classOf[Sequence]).inc("count")
      val ret: Sequence = ds.findAndModify(query, ops)
      ret.count
    }
  }

  //  // 创建讨论组
  def createChatGroup(creator: Long, name: String, chatGroup: Map[ChatGroupProp, String], members: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val futureGid = populateGroupId()(ds, futurePool)
    // 如果讨论组创建人未选择其他的人，那么就创建者自己一个人，如果选择了其他人，那么群成员便是创建者和其他创建者拉进来的人
    val participants = (members :+ creator).toSet
    for {
      gid <- futureGid
    } yield {
      val cg = ChatGroup(creator, gid, name, participants.toSeq)
      chatGroup foreach (item => {
        item._1 match {
          case ChatGroupProp.GroupDesc => cg.groupDesc = item._2
          case ChatGroupProp.Avatar => cg.avatar = item._2
          case ChatGroupProp.GroupType => cg.groupType = item._2
          case _ => ""
        }
      })
      if (cg != null) {
        cg.admin = Seq(creator)
        cg.maxUsers = Constant.maxUsers
        cg.createTime = java.lang.System.currentTimeMillis()
        cg.updateTime = java.lang.System.currentTimeMillis()
        ds.save[ChatGroup](cg)
      } else throw new NotFoundException("Create chat group instance failure")
    }
  }

  //  // 获取讨论组信息
  def getChatGroup(chatGroupId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Option[yunkai.ChatGroup]] =
    futurePool {
      val entry = ds.find(classOf[ChatGroup], "chatGroupId", chatGroupId).get()
      if (entry == null) None
      else {
        Some(yunkai.ChatGroup(entry.chatGroupId, entry.name, toOption(entry.groupDesc), entry.groupType, toOption(entry.avatar), toOption(entry.tags),
          entry.creator, entry.admin, entry.participants, toOption(entry.msgCounter), entry.maxUsers, entry.createTime, entry.updateTime, entry.visible))
      }
    }

  // 修改讨论组信息（比如名称、描述等）
  def updateChatGroup(chatGroupId: Long, chatGroup: Map[ChatGroupProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] = futurePool {
    val query = ds.find(classOf[ChatGroup], "chatGroupId", chatGroupId)
    val updateOps = chatGroup.foldLeft(ds.createUpdateOperations(classOf[ChatGroup]))((ops, entry) => {
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

  // 获取用户讨论组信息*****
  def getUserChatGroups(userId: Long, fields: Option[Seq[ChatGroupProp]] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[yunkai.ChatGroup]] = {
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
      val result = query.asList().toSeq.filter(_ != null)
      for {
        item <- result
      } yield {
        yunkai.ChatGroup(item.chatGroupId, item.name, toOption(item.groupDesc), item.groupType, toOption(item.avatar), toOption(item.tags),
          item.creator, item.admin, item.participants, toOption(item.msgCounter), item.maxUsers, item.createTime, item.updateTime, item.visible)
      }
    }
  }

  // 批量添加讨论组成员
  def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      // 更新讨论组的成员列表
      val queryChatGroup = ds.find(classOf[ChatGroup], "chatGroupId", chatGroupId)
      val chatGroupUpdateOps = ds.createUpdateOperations(classOf[ChatGroup]).addAll(ChatGroup.fdParticipants, userIds, false)
      ds.updateFirst(queryChatGroup, chatGroupUpdateOps)

      // 更新Conversation的成员列表
      val queryConversation = ds.find(classOf[Conversation], "id", queryChatGroup.get().getId)
      val conversationUpdateOps = ds.createUpdateOperations(classOf[Conversation]).addAll(Conversation.fdParticipants, userIds, false)
      ds.updateFirst(queryConversation, conversationUpdateOps)
    }

  // 批量删除讨论组成员
  def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Unit] =
    futurePool {
      // 更新讨论组的成员列表
      val queryChatGroup = ds.find(classOf[ChatGroup], "chatGroupId", chatGroupId)
      val chatGroupUpdateOps = ds.createUpdateOperations(classOf[ChatGroup]).removeAll(ChatGroup.fdParticipants, userIds)
      ds.updateFirst(queryChatGroup, chatGroupUpdateOps)

      // 更新Conversation的成员列表
      val queryConversation = ds.find(classOf[Conversation], "id", queryChatGroup.get().getId)
      val conversationUpdateOps = ds.createUpdateOperations(classOf[Conversation]).removeAll(Conversation.fdParticipants, userIds)
      ds.updateFirst(queryConversation, conversationUpdateOps)
    }

  // 获得讨论组成员*************
  def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[yunkai.UserInfo]] =
    futurePool {
      val query = ds.find(classOf[ChatGroup], "chatGroupId", chatGroupId).get().participants
      val queryUserInfo = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(query)
      val retrievedFields = fields.getOrElse(Seq()) map {
        case UserInfoProp.UserId => UserInfo.fdUserId
        case UserInfoProp.NickName => UserInfo.fdNickName
        case UserInfoProp.Avatar => UserInfo.fdAvatar
        case _ => ""
      } filter (_ nonEmpty)
      if (retrievedFields nonEmpty)
        queryUserInfo.retrievedFields(true, retrievedFields :+ UserInfo.fdUserId: _*)
      val result = queryUserInfo.toList
      //将List<UserInfo>转为list<yunkai.UserInfo>
      for {
        item <- result
      } yield {
        yunkai.UserInfo(item.userId, item.nickName, toOption(item.avatar), None, toOption(item.signature), toOption(item.tel))
      }
    }
}
