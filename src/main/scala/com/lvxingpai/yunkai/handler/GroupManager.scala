package com.lvxingpai.yunkai.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ BooleanNode, LongNode, NullNode, TextNode }
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.{ ChatGroup, Conversation, UserInfo }
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.language.{ implicitConversions, postfixOps }

/**
 * Created by zephyre on 6/19/15.
 */
object GroupManager {
  /**
   * 将ChatGroupProp转换为字段名称
   *
   * @param prop
   * @return
   */
  implicit def chatGroupPropToFieldName(prop: ChatGroupProp): String = {
    prop match {
      case ChatGroupProp.ChatGroupId => ChatGroup.fdChatGroupId
      case ChatGroupProp.Name => ChatGroup.fdName
      case ChatGroupProp.GroupDesc => ChatGroup.fdGroupDesc
      case ChatGroupProp.Avatar => ChatGroup.fdAvatar
      case ChatGroupProp.Tags => ChatGroup.fdTags
      case ChatGroupProp.Creator => ChatGroup.fdCreator
      case ChatGroupProp.Admin => ChatGroup.fdAdmin
      case ChatGroupProp.Participants => ChatGroup.fdParticipants
      case ChatGroupProp.MaxUsers => ChatGroup.fdMaxUsers
      case ChatGroupProp.Visible => ChatGroup.fdVisible
      case _ => throw new IllegalArgumentException("Illegal property name: %s" format prop.toString)
    }
  }

  /**
   * 获得用户参加的讨论组的个数
   * @param userId
   * @return
   */
  def getUserChatGroupCount(userId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Int] = futurePool {
    val result = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(userId)
      .retrievedFields(true, UserInfo.fdChatGroups).get()
    if (result == null)
      throw NotFoundException(s"Cannot find user $userId")
    else
      result.chatGroups.length
  }

  def createChatGroup(creator: Long, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, Any] = Map())(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] = {
    val futureGid = IdGenerator.generateId("yunkai.newChatGroupId")
    // 如果讨论组创建人未选择其他的人，那么就创建者自己一个人，如果选择了其他人，那么群成员便是创建者和其他创建者拉进来的人
    val participants = (members :+ creator).toSet.toSeq
    val cgFuture = for {
      gid <- futureGid
    } yield {
      val cg = ChatGroup(creator, gid, participants)
      chatGroupProps foreach (item => {
        item._1 match {
          case ChatGroupProp.Name => cg.name = item._2.toString
          case ChatGroupProp.GroupDesc => cg.groupDesc = item._2.toString
          case ChatGroupProp.Avatar => cg.avatar = item._2.toString
          case ChatGroupProp.Tags => cg.tags = item._2.asInstanceOf[Seq[String]]
          case ChatGroupProp.MaxUsers => cg.maxUsers = item._2.asInstanceOf[Int]
          case ChatGroupProp.Visible => cg.visible = item._2.asInstanceOf[Boolean]
          case _ => ""
        }
      })
      cg.admin = Seq(creator)
      cg.createTime = java.lang.System.currentTimeMillis()

      // 1. gid重复 2. 数据库通信异常  3. 切面
      ds.save[ChatGroup](cg)

      // 触发创建讨论组的事件
      val eventArgs = scala.collection.immutable.Map(
        "chatGroupId" -> LongNode.valueOf(cg.chatGroupId),
        "name" -> TextNode.valueOf(cg.name),
        "groupDesc" -> (if (cg.groupDesc != null && cg.groupDesc.nonEmpty) TextNode.valueOf(cg.groupDesc) else NullNode.getInstance()),
        "avatar" -> (if (cg.avatar != null && cg.avatar.nonEmpty) TextNode.valueOf(cg.avatar) else NullNode.getInstance()),
        "tags" -> (new ObjectMapper).valueToTree(cg.tags),
        "admin" -> (new ObjectMapper).valueToTree(cg.admin),
        "participants" -> (new ObjectMapper).valueToTree(cg.participants),
        "visible" -> BooleanNode.valueOf(cg.visible)
      )
      EventEmitter.emitEvent(EventEmitter.evtCreateChatGroup, eventArgs)
      cg
    }

    cgFuture map (cg => {
      // 在每个参与用户的chatGroups字段中，添加本ChatGroup的信息
      cg.participants map (uid => {
        val query = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(uid)
        val ops = ds.createUpdateOperations(classOf[UserInfo]).add(UserInfo.fdChatGroups, cg.chatGroupId, false)
        ds.updateFirst(query, ops)
      })
      cg
    })
  }

  // 获取讨论组信息
  def getChatGroup(chatGroupId: Long, fields: Seq[ChatGroupProp] = Seq[ChatGroupProp]())(implicit ds: Datastore, futurePool: FuturePool): Future[Option[ChatGroup]] = futurePool {
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, (fields :+ ChatGroupProp.ChatGroupId) map chatGroupPropToFieldName: _*)
    Option(query.get())
  }

  //TODO 实现
  def getChatGroups(fields: Seq[ChatGroupProp], groupIdList: Long*): Future[Map[Long, Option[ChatGroup]]] = {
    null
  }

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
      "tags" -> new ObjectMapper().valueToTree(result.tags),
      "admin" -> new ObjectMapper().valueToTree(result.admin),
      "visible" -> BooleanNode.valueOf(result.visible)
    )
    EventEmitter.emitEvent(EventEmitter.evtModChatGroup, eventArgs)

    result
  }

  // 获取用户讨论组信息
  def getUserChatGroups(userId: Long, fields: Seq[ChatGroupProp] = Seq(), offset: Option[Int] = None,
    limit: Option[Int] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[ChatGroup]] = {
    // 默认最大的获取数量
    val maxCount = 100

    AccountManager.getUserById(userId, Seq(UserInfoProp.ChatGroups)) map (u => {
      if (u isEmpty)
        throw NotFoundException(s"Cannot find user $userId")
      else {
        u.get.chatGroups
      }
    }) flatMap (groupIdList => {
      // 分页机制
      val idList = if ((offset nonEmpty) || (limit nonEmpty)) {
        val s = offset.getOrElse(0)
        val e = s + limit.getOrElse(maxCount)
        groupIdList.slice(s, e)
      } else
        groupIdList
      getChatGroups(fields, idList: _*)
    }) map (groupMap => {
      (groupMap filter (_._2 nonEmpty) map (_._2.get)).toSeq
    })
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
        "participants" -> new ObjectMapper().valueToTree(chatGroup.participants)
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
        "participants" -> new ObjectMapper().valueToTree(chatGroup.participants)
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
