package com.lvxingpai.yunkai.handler

import com.fasterxml.jackson.databind.node._
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.lvxingpai.yunkai.model.{ChatGroup, UserInfo}
import com.mongodb.{BasicDBList, BasicDBObject, BasicDBObjectBuilder}
import com.twitter.util.{Future, FuturePool}
import org.mongodb.morphia.Datastore

import scala.collection.JavaConversions._

//import scala.collection.Map

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
      case ChatGroupProp.Id => ChatGroup.fdId
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

  implicit def chatGroup2JsonNode(chatGroup: ChatGroup): ObjectNode = {
    val targets = new ObjectMapper().createObjectNode()
    targets.put("id", chatGroup.chatGroupId)
    targets.put("name", chatGroup.name)
    val avatarValue = Option(chatGroup.avatar).getOrElse("")
    targets.put("avatar", avatarValue)
    targets
  }

  /**
   * 获得用户参加的讨论组的个数
   * @param userId
   * @return
   */
  def getUserChatGroupCount(userId: Long)(implicit ds: Datastore, futurePool: FuturePool): Future[Int] = futurePool {
    ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdParticipants).hasThisOne(userId).countAll().toInt
  }

  def createChatGroup(creator: Long, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, Any] = Map())(implicit ds: Datastore, futurePool: FuturePool): Future[ChatGroup] = {
    // TODO 创建的时候判断是否超限，如果是的话，抛出GroupMemberLimitException异常。同时别忘了修改users.thrift，将这个异常添加到声明列表中
    val futureGid = IdGenerator.generateId("yunkai.newChatGroupId")

    // 如果讨论组创建人未选择其他的人，那么就创建者自己一个人，如果选择了其他人，那么群成员便是创建者和其他创建者拉进来的人
    val participants = (members :+ creator).toSet.toSeq

    // 获得相关用户的详情
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    val futureUsers = AccountManager.getUsersByIdList(responseFields, participants: _*)

    for {
      gid <- futureGid
      userMap <- futureUsers
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
      // 检查创建的群的用户数是否超过最大的上限
      if (participants.size > cg.maxUsers)
        throw GroupMembersLimitException("Chat group members' number exceed maximum allowable")
      else
        ds.save[ChatGroup](cg) // 1. gid重复 2. 数据库通信异常  3. 切面

      // 触发创建讨论组的事件
      import AccountManager.user2JsonNode
      import Implicits.JsonConversions._

      val eventArgs: Map[String, JsonNode] = Map(
        "chatGroupId" -> cg.chatGroupId,
        "name" -> cg.name,
        "avatar" -> cg.avatar,
        "creator" -> userMap(creator).get,
        "participants" -> cg.participants
      )
      EventEmitter.emitEvent(EventEmitter.evtCreateChatGroup, eventArgs)

      cg
    }
  }

  // 获取讨论组信息
  def getChatGroup(chatGroupId: Long, fields: Seq[ChatGroupProp] = Seq[ChatGroupProp]())(implicit ds: Datastore, futurePool: FuturePool): Future[Option[ChatGroup]] = futurePool {
    val allowedProperties = Seq(ChatGroupProp.Name, ChatGroupProp.GroupDesc, ChatGroupProp.ChatGroupId,
      ChatGroupProp.Avatar, ChatGroupProp.Tags, ChatGroupProp.Creator, ChatGroupProp.Admin, ChatGroupProp.Participants,
      ChatGroupProp.MaxUsers, ChatGroupProp.Visible)
    val retrievedFields = (fields filter (allowedProperties.contains(_))) ++ Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Id) map
      chatGroupPropToFieldName

    val group = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, retrievedFields: _*).get()

    Option(group)
  }

  //TODO 实现
  def getChatGroups(fields: Seq[ChatGroupProp], groupIdList: Long*)(implicit ds: Datastore, futurePool: FuturePool): Future[Map[Long, Option[ChatGroup]]] = {
    null
    //    val allowedProperties = Seq(ChatGroupProp.Name, ChatGroupProp.GroupDesc, ChatGroupProp.ChatGroupId,
    //      ChatGroupProp.Avatar, ChatGroupProp.Tags, ChatGroupProp.Creator, ChatGroupProp.Admin, ChatGroupProp.Participants,
    //      ChatGroupProp.MaxUsers, ChatGroupProp.Visible)
    //    val retrievedFields = (fields filter (allowedProperties.contains(_))) :+ ChatGroupProp.ChatGroupId map
    //      chatGroupPropToFieldName
    //    futurePool {
    //      val result = for (elem <- groupIdList) {
    //        ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(elem).retrievedFields(true, retrievedFields: _*).get()
    //      }
    //      val result = for {elem <- groupIdList} {
    //        ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(elem).retrievedFields(true, retrievedFields: _*).get()
    //      }
    //      Option(group)
    //    }
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
    import Implicits.JsonConversions._

    val eventArgs: Map[String, JsonNode] = Map(
      "chatGroupId" -> result.chatGroupId
    )
    EventEmitter.emitEvent(EventEmitter.evtModChatGroup, eventArgs)

    Option(result)
  }

  // 获取用户讨论组信息
  def getUserChatGroups(userId: Long, fields: Seq[ChatGroupProp] = Seq(), offset: Option[Int] = None,
                        limit: Option[Int] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[ChatGroup]] = {
    import ChatGroup._

    // 默认最大的获取数量
    val maxCount = 100

    val retrievedFields = ((fields ++ Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Id) map {
      case ChatGroupProp.ChatGroupId => fdChatGroupId
      case ChatGroupProp.Name => fdName
      case ChatGroupProp.Participants => fdParticipants
      case ChatGroupProp.MaxUsers => fdMaxUsers
      case ChatGroupProp.Tags => fdTags
      case ChatGroupProp.Visible => fdVisible
      case ChatGroupProp.Avatar => fdAvatar
      case ChatGroupProp.Creator => fdCreator
      case ChatGroupProp.GroupDesc => fdGroupDesc
      case ChatGroupProp.Admin => fdAdmin
      case _ => ""
    } filter (_.nonEmpty)) :+ fdChatGroupId).toSet.toSeq

    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdParticipants).hasThisOne(userId)
      .retrievedFields(true, retrievedFields: _*).offset(offset getOrElse 0).limit(limit getOrElse maxCount)

    futurePool {
      query.asList().toSeq
    }
  }

  // 批量添加讨论组成员
  def addChatGroupMembers(chatGroupId: Long, operatorId: Long, userIdsToAdd: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[Long]] = {
    // 获得ChatGroup的最大人数
    val futureGroup = GroupManager.getChatGroup(chatGroupId, Seq(ChatGroupProp.MaxUsers, ChatGroupProp.Participants))

    // 查看是否所有的userId都有效
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    val futureUsers = AccountManager.getUsersByIdList(responseFields, userIdsToAdd :+ operatorId: _*)

    // 在ChatGroup.participants中添加
    def func1(group: Option[ChatGroup], users: Map[Long, Option[UserInfo]]): Future[(Seq[Long], Seq[UserInfo])] = futurePool {
      if (group.isEmpty || users.exists(_._2.isEmpty))
        throw NotFoundException("Cannot find all the users and the chat group")
      else {
        val col = MorphiaFactory.getCollection(classOf[ChatGroup])

        val usersToAdd = (users map (_._2.get)).toSeq
        val maxUsers = group.get.maxUsers
        val fieldName = ChatGroup.fdParticipants
        val addUserCount = usersToAdd.length
        val funcSpec =
          s"""var l = (this.$fieldName == null) ? 0 : this.$fieldName.length;
                                                                       |return l + $addUserCount <= $maxUsers""".stripMargin.trim

        val query = BasicDBObjectBuilder.start().add(ChatGroup.fdChatGroupId, chatGroupId).add("$where", funcSpec).get()
        val fields = BasicDBObjectBuilder.start(Map(ChatGroup.fdParticipants -> 1, ChatGroup.fdMaxUsers -> 1)).get()
        val sort = new BasicDBObject()
        val ops = new BasicDBObject("$push",
          new BasicDBObject(ChatGroup.fdParticipants,
            BasicDBObjectBuilder.start()
              .add("$each", bufferAsJavaList(userIdsToAdd.toBuffer)).add("$slice", maxUsers).get()))
        val doc = col.findAndModify(query, fields, sort, false, ops, true, false)
        if (doc == null)
          throw GroupMembersLimitException("")
        else {
          val remainedParticipants = doc.get(ChatGroup.fdParticipants).asInstanceOf[BasicDBList].toSeq map (_.asInstanceOf[Long])
          remainedParticipants -> usersToAdd
        }
      }
    }

    def emitEvent(operatorInfo: UserInfo, addedUsers: Seq[UserInfo]): Future[Unit] = {
      // 触发添加讨论组成员的事件
      // 查找待添加的用户信息
      val userInfos = new ObjectMapper().createObjectNode()
      for (elem <- addedUsers) {
        val userInfo = elem
        userInfos.put("userId", userInfo.userId)
        userInfos.put("nickName", userInfo.nickName)
        userInfos.put("avatar", userInfo.avatar)
      }
      //      val participantsNode = new ObjectMapper().createArrayNode()
      //      participants foreach participantsNode.add

      import AccountManager.user2JsonNode
      import Implicits.JsonConversions._

      val eventArgs: Map[String, JsonNode] = Map(
        "chatGroupId" -> chatGroupId,
        "operator" -> operatorInfo,
        "targets" -> userInfos
      )
      futurePool {
        EventEmitter.emitEvent(EventEmitter.evtAddGroupMembers, eventArgs)
      }
    }

    for {
      group <- futureGroup
      users <- futureUsers // Users to be removed(with both userId and nickName available)
      entry <- func1(group, users)
      _ <- emitEvent(users(operatorId).get, entry._2)
    } yield {
      entry._1
    }
  }

  // 批量删除讨论组成员
  def removeChatGroupMembers(chatGroupId: Long, operatorId: Long, userToRemove: Seq[Long])(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[Long]] = {
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    // 查看operatorId是否有效
    val futureOperator = AccountManager.getUserById(operatorId, responseFields)
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, ChatGroup.fdParticipants)
    val ops = ds.createUpdateOperations(classOf[ChatGroup]).removeAll(ChatGroup.fdParticipants, bufferAsJavaList(userToRemove.toBuffer))
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
    // 查看是否所有的userId都有效
    val futureUsers = AccountManager.getUsersByIdList(Seq(UserInfoProp.UserId, UserInfoProp.NickName), userToRemove: _*)
    val userInfos: Seq[UserInfo] = Seq()
    for {
      users <- futureUsers
    } yield {
      for (elem <- users.values.toSeq)
        userInfos.add(elem.get)
    }

    //    // 更新Conversation
    //    def procConversation(group: ChatGroup): Future[ChatGroup] = {
    //      // 更新Conversation的成员列表
    //      // 目前先这么做，后面给成观察者模式
    //      val queryConversation = ds.createQuery(classOf[Conversation]).field(Conversation.fdId).equal(chatGroupId)
    //      val conversationUpdateOps = ds.createUpdateOperations(classOf[Conversation]).removeAll(Conversation.fdParticipants, userIds)
    //      futurePool {
    //        ds.updateFirst(queryConversation, conversationUpdateOps)
    //        group
    //      }
    //    }

    // 触发删除讨论组成员的事件
    def procEvtEmitter(group: ChatGroup, operator: UserInfo, removedUsers: Seq[UserInfo]): Future[ChatGroup] = {
      // 触发删除讨论组成员的事件
      // 查找待添加的用户信息
      val userInfos = new ObjectMapper().createObjectNode()
      for (elem <- removedUsers) {
        val userInfo = elem
        userInfos.put("userId", userInfo.userId)
        userInfos.put("nickName", userInfo.nickName)
        userInfos.put("avatar", userInfo.avatar)
      }

      //      val participantsNode = new ObjectMapper().createArrayNode()
      //      group.participants foreach participantsNode.add

      import AccountManager.user2JsonNode

      val eventArgs: Map[String, JsonNode] = Map(
        "chatGroupId" -> group,
        "operator" -> operator,
        "targets" -> userInfos
      )
      futurePool {
        EventEmitter.emitEvent(EventEmitter.evtRemoveGroupMembers, eventArgs)
        group
      }
    }

    for {
      group <- groupFuture
      group2 <- verify(group)
      operator <- futureOperator
      users <- futureUsers
      _ <- procEvtEmitter(group2, operator.get, userInfos)
    } yield {
      group.participants
    }
  }

  // 获得讨论组成员
  def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]] = None)(implicit ds: Datastore, futurePool: FuturePool): Future[Seq[UserInfo]] =
    futurePool {
      val query = ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, chatGroupId).get().participants
      val queryUserInfo = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).in(query)
      val retrievedFields = fields.getOrElse(Seq()) ++ Seq(UserInfoProp.UserId, UserInfoProp.Id) map {
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
