package com.lvxingpai.yunkai.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{BooleanNode, LongNode, NullNode, TextNode}
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.lvxingpai.yunkai.model.{ChatGroup, Conversation, UserInfo}
import com.mongodb.{BasicDBList, BasicDBObject, BasicDBObjectBuilder}
import com.twitter.util.{Future, FuturePool}
import org.mongodb.morphia.Datastore

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.language.{implicitConversions, postfixOps}

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
    // TODO 创建的时候判断是否超限，如果是的话，抛出GroupMemberLimitException异常。同时别忘了修改users.thrift，将这个异常添加到声明列表中
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
    val allowedProperties = Seq(ChatGroupProp.Name, ChatGroupProp.GroupDesc, ChatGroupProp.ChatGroupId,
      ChatGroupProp.Avatar, ChatGroupProp.Tags, ChatGroupProp.Creator, ChatGroupProp.Admin, ChatGroupProp.Participants,
      ChatGroupProp.MaxUsers, ChatGroupProp.Visible)
    val retrievedFields = (fields filter (allowedProperties.contains(_))) :+ ChatGroupProp.ChatGroupId map
      chatGroupPropToFieldName

    val group = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, retrievedFields: _*).get()

    Option(group)
  }

  //TODO 实现
  def getChatGroups(fields: Seq[ChatGroupProp], groupIdList: Long*): Future[Map[Long, Option[ChatGroup]]] = {
    null
  }

  // 修改讨论组信息（比如名称、描述等）
  def updateChatGroup(chatGroupId: Long, chatGroupProps: Map[ChatGroupProp, Any])(implicit ds: Datastore, futurePool: FuturePool): Future[Option[ChatGroup]] = futurePool {
    // 所有被修改的字段都需要返回
    val retrievedFields = chatGroupProps.keySet.toSeq :+ ChatGroupProp.ChatGroupId map chatGroupPropToFieldName
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, retrievedFields: _*)
    val result = if (query isEmpty)
      throw new NotFoundException(s"ChatGroup chatGroupId=$chatGroupId not found, update failure")
    else {
      val updateOps = chatGroupProps.foldLeft(ds.createUpdateOperations(classOf[ChatGroup]))((ops, entry) => {
        val (key, value) = entry
        key match {
          case ChatGroupProp.Name => ops.set(ChatGroup.fdName, value)
          case ChatGroupProp.GroupDesc => ops.set(ChatGroup.fdGroupDesc, value)
          case ChatGroupProp.Avatar => ops.set(ChatGroup.fdAvatar, value)
          case ChatGroupProp.Tags => ops.set(ChatGroup.fdTags, value)
          case ChatGroupProp.Visible => ops.set(ChatGroup.fdVisible, value)
          case ChatGroupProp.MaxUsers => ops.set(ChatGroup.fdMaxUsers, value)
          case _ => ops
        }
      })
      ds.findAndModify(query, updateOps)
    }

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

    Option(result)
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
        groupIdList.slice(s, e).toSeq
      } else {
        groupIdList.toSeq
      }
      getChatGroups(fields, idList: _*)
    }) map (groupMap => {
      (groupMap filter (_._2 nonEmpty) map (_._2.get)).toSeq
    })
  }

  // 批量添加讨论组成员
  def addChatGroupMembers(chatGroupId: Long, userIds: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[Long]] = {
    // 获得ChatGroup的最大人数
    val futureGroup = GroupManager.getChatGroup(chatGroupId, Seq(ChatGroupProp.MaxUsers))
    // 查看是否所有的userId都有效
    val futureUsers = AccountManager.getUsersByIdList(Seq(UserInfoProp.UserId), userIds: _*)

    // 在ChatGroup.participants中添加
    def func1(group: Option[ChatGroup], users: Map[Long, Option[UserInfo]]): Future[(Seq[Long], Seq[Long])] = futurePool {
      if (group.isEmpty || users.filter(_._2.isEmpty).nonEmpty)
        throw NotFoundException("Cannot find all the users and the chat group")
      else {
        val col = MorphiaFactory.getCollection(classOf[ChatGroup])

        val maxUsers = group.get.maxUsers
        val fieldName = ChatGroup.fdParticipants
        val addUserCount = userIds.length
        val funcSpec =
          s"""var l = (this.$fieldName == null) ? 0 : this.$fieldName.length;
                                                                       |return l + $addUserCount <= $maxUsers""".stripMargin.trim

        val query = BasicDBObjectBuilder.start().add(ChatGroup.fdChatGroupId, chatGroupId).add("$where", funcSpec).get()
        val fields = BasicDBObjectBuilder.start(Map(ChatGroup.fdParticipants -> 1, ChatGroup.fdMaxUsers -> 1)).get()
        val sort = new BasicDBObject()
        val ops = new BasicDBObject("$push",
          new BasicDBObject(ChatGroup.fdParticipants,
            BasicDBObjectBuilder.start()
              .add("$each", bufferAsJavaList(userIds.toBuffer)).add("$slice", maxUsers).get()))
        val doc = col.findAndModify(query, fields, sort, false, ops, true, false)
        if (doc == null)
          throw GroupMembersLimitException("")
        else
          userIds -> (doc.get(ChatGroup.fdParticipants).asInstanceOf[BasicDBList].toSeq map (_.asInstanceOf[Long]))
      }
    }

    // 在Conversation中添加participants
    def func2(group: ChatGroup, userIds: Seq[Long]): Future[Unit] = futurePool {
      val query = ds.createQuery(classOf[Conversation]).field(Conversation.fdId).equal(group.id)
        .retrievedFields(true, Conversation.fdId)
      val ops = ds.createUpdateOperations(classOf[Conversation]).addAll(Conversation.fdParticipants, userIds, false)
      ds.findAndModify(query, ops)
    }

    for {
      group <- futureGroup
      users <- futureUsers
      entry <- func1(group, users)
      _ <- func2(group.get, entry._1)
    } yield entry._2
  }

  // 批量删除讨论组成员
  def removeChatGroupMembers(chatGroupId: Long, userIds: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[Long]] = {
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, ChatGroup.fdParticipants)
    val ops = ds.createUpdateOperations(classOf[ChatGroup]).removeAll(ChatGroup.fdParticipants, bufferAsJavaList(userIds.toBuffer))
    val groupFuture = futurePool {
      ds.findAndModify(query, ops)
    }

    // 验证chatGroupId是否有误
    def verify(group: ChatGroup): Future[ChatGroup] = {
      if (group == null)
        throw NotFoundException(s"Cannot find chat group $chatGroupId")
      else
        futurePool {
          group
        }
    }

    // 更新Conversation
    def procConversation(group: ChatGroup): Future[ChatGroup] = {
      // 更新Conversation的成员列表
      // 目前先这么做，后面给成观察者模式
      val queryConversation = ds.createQuery(classOf[Conversation]).field(Conversation.fdId).equal(chatGroupId)
      val conversationUpdateOps = ds.createUpdateOperations(classOf[Conversation]).removeAll(Conversation.fdParticipants, userIds)
      futurePool {
        ds.updateFirst(queryConversation, conversationUpdateOps)
        group
      }
    }

    // 触发事件
    def procEvtEmitter(group: ChatGroup): Future[ChatGroup] = {
      // 触发添加/删除讨论组成员的事件
      val eventArgs = scala.collection.immutable.Map(
        "chatGroupId" -> LongNode.valueOf(chatGroupId),
        "participants" -> new ObjectMapper().valueToTree(group.participants)
      )
      futurePool {
        EventEmitter.emitEvent(EventEmitter.evtRemoveGroupMembers, eventArgs)
        group
      }
    }

    for {
      group <- groupFuture
      group2 <- verify(group)
      _ <- procConversation(group2)
      _ <- procEvtEmitter(group2)
    } yield {
      group.participants
    }
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
