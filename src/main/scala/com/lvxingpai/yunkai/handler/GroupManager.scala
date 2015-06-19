package com.lvxingpai.yunkai.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ BooleanNode, LongNode, NullNode, TextNode }
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.{ ChatGroup, Conversation, UserInfo }
import com.mongodb.DuplicateKeyException
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.language.postfixOps

/**
 * Created by zephyre on 6/19/15.
 */
object GroupManager {

  def createChatGroup(creator: Long, name: String, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, String])(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] = {
    val futureGid = IdGenerator.generateId("yunkai.newChatGroupId")
    // 如果讨论组创建人未选择其他的人，那么就创建者自己一个人，如果选择了其他人，那么群成员便是创建者和其他创建者拉进来的人
    val participants = (members :+ creator).toSet
    for {
      gid <- futureGid
    } yield {
      val cg = ChatGroup(creator, gid, participants.toSeq)
      chatGroupProps foreach (item => {
        item._1 match {
          case ChatGroupProp.GroupDesc => cg.groupDesc = item._2
          case ChatGroupProp.Avatar => cg.avatar = item._2
          //          case ChatGroupProp.GroupType => cg.groupType = item._2
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
          //          "groupType" -> TextNode.valueOf(cg.groupType),
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
  def getChatGroup(chatGroupId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] = futurePool {
    ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId).get()
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
      //      case ChatGroupProp.GroupType => ChatGroup.fdGroupType
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
