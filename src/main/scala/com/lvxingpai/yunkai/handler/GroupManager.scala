package com.lvxingpai.yunkai.handler

import com.fasterxml.jackson.databind.node._
import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.google.inject.Inject
import com.google.inject.name.Named
import com.lvxingpai.idgen.IdGen
import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.Implicits.JsonConversions._
import com.lvxingpai.yunkai._
import com.lvxingpai.yunkai.model.ChatGroup
import com.mongodb.{ BasicDBList, BasicDBObject, BasicDBObjectBuilder, DBCollection }
import com.twitter.util.{ Future, FuturePool }
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.annotations.Property

import scala.collection.JavaConversions._

//import scala.collection.Map

import scala.language.{ implicitConversions, postfixOps }

/**
 * Created by zephyre on 6/19/15.
 */
class GroupManager @Inject() (@Named("yunkai") ds: Datastore, implicit val futurePool: FuturePool) {

  lazy val accountManager = Global.injector.getInstance(classOf[AccountManager])

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
    //    targets.put("id", chatGroup.id.toString)
    targets.put("chatGroupId", chatGroup.chatGroupId)
    targets.put("name", chatGroup.name)
    //    val avatarValue = Option(chatGroup.avatar).getOrElse("")
    //    targets.put("avatar", avatarValue)
    targets
  }

  /**
   * 获得用户参加的讨论组的个数
   * @param userId
   * @return
   */
  def getUserChatGroupCount(userId: Long): Future[Int] = futurePool {
    ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdParticipants).hasThisOne(userId).countAll().toInt
  }

  def createChatGroup(creator: Long, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, Any] = Map()): Future[ChatGroup] = {
    // TODO 创建的时候判断是否超限，如果是的话，抛出GroupMemberLimitException异常。同时别忘了修改users.thrift，将这个异常添加到声明列表中
    val futureGid = Global.injector.getInstance(classOf[IdGen.FinagledClient]).generate("chatGroup")

    // 如果讨论组创建人未选择其他的人，那么就创建者自己一个人，如果选择了其他人，那么群成员便是创建者和其他创建者拉进来的人
    val participants = (members :+ creator).toSet.toSeq

    // 获得相关用户的详情
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    val futureUsers = accountManager.getUsersByIdList(responseFields, None, participants: _*)

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
        throw GroupMembersLimitException(Some("Chat group members' number exceed maximum allowable"))
      else
        ds.save[ChatGroup](cg) // 1. gid重复 2. 数据库通信异常  3. 切面

      // 触发创建讨论组的事件
      val targets = new ObjectMapper().createObjectNode()
      for (elem <- userMap) {
        targets.put("id", elem._2.get.id.toString)
        targets.put("userId", elem._2.get.userId)
        targets.put("nickName", elem._2.get.nickName)
        val avatarValue = elem._2.get.avatar.getOrElse("")

        targets.put("avatar", avatarValue)
      }
      val eventArgs: Map[String, JsonNode] = Map(
        "chatGroup" -> cg,
        "creator" -> userMap(creator).get,
        "members" -> targets
      )
      EventEmitter.emitEvent(EventEmitter.evtCreateChatGroup, eventArgs)

      cg
    }
  }

  // 获取讨论组信息
  def getChatGroup(chatGroupId: Long, fields: Seq[ChatGroupProp] = Seq[ChatGroupProp]()): Future[Option[ChatGroup]] = futurePool {
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
  def getChatGroups(fields: Seq[ChatGroupProp], groupIdList: Long*): Future[Map[Long, Option[ChatGroup]]] = {
    val allowedProperties = Seq(ChatGroupProp.Name, ChatGroupProp.GroupDesc, ChatGroupProp.ChatGroupId,
      ChatGroupProp.Avatar, ChatGroupProp.Tags, ChatGroupProp.Creator, ChatGroupProp.Admin, ChatGroupProp.Participants,
      ChatGroupProp.MaxUsers, ChatGroupProp.Visible)
    val retrievedFields = (fields filter (allowedProperties.contains(_))) :+ ChatGroupProp.ChatGroupId map
      chatGroupPropToFieldName
    futurePool {
      if (groupIdList isEmpty) {
        Map[Long, Option[ChatGroup]]()
      } else {
        val query = groupIdList length match {
          case 1 => ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(groupIdList head)
          case _ => ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).in(seqAsJavaList(groupIdList))
        }

        query.retrievedFields(true, retrievedFields: _*)
        val results = Map(query.asList() map (v => v.chatGroupId -> v): _*)
        Map(groupIdList map (v => v -> (results get v)): _*)
      }
    }
  }

  // 修改讨论组信息（比如名称、描述等）
  def updateChatGroup(chatGroupId: Long, operatorId: Long, chatGroupProps: Map[ChatGroupProp, Any]): Future[Option[ChatGroup]] = {
    // 检查修改人operatorId是否有效
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    val operator = accountManager.getUserById(operatorId, responseFields, None)

    def func1(): Future[Option[ChatGroup]] = futurePool {
      // 所有被修改的字段都需要返回
      val retrievedFields = chatGroupProps.keySet.toSeq :+ ChatGroupProp.ChatGroupId map chatGroupPropToFieldName
      val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
        .retrievedFields(true, retrievedFields: _*)
      val result = if (query isEmpty)
        throw new NotFoundException(Some(s"ChatGroup chatGroupId=$chatGroupId not found, update failure"))
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
      Option(result)
    }
    // 验证operatorId是否有误
    def verify(user: Option[yunkai.UserInfo]): Future[yunkai.UserInfo] = {
      if (user isEmpty)
        throw NotFoundException(Some(s"Cannot find chat group $chatGroupId"))
      else
        futurePool {
          user.get
        }
    }
    //    if(elem isEmpty)
    //      throw NotFoundException(Some(s"Can not found such =$operatorId"))
    def emitEvent(group: Option[ChatGroup], operatorInfo: yunkai.UserInfo): Future[Unit] = {
      // 触发修改讨论组属性的事件
      val updateInfo = new ObjectMapper().createObjectNode()
      updateInfo.put("name", "New group name")
      updateInfo.put("desc", "New group desc")

      import Implicits.JsonConversions._
      val eventArgs: Map[String, JsonNode] = Map(
        "chatGroupId" -> group.get.chatGroupId,
        "operator" -> operatorInfo,
        "updateInfo" -> updateInfo
      )
      futurePool {
        EventEmitter.emitEvent(EventEmitter.evtModChatGroup, eventArgs)
      }
    }
    for {
      user <- operator
      user1 <- verify(user)
      entry <- func1()
      _ <- emitEvent(entry, user1)
    } yield {
      entry
    }
  }

  // 获取用户讨论组信息
  def getUserChatGroups(userId: Long, fields: Seq[ChatGroupProp] = Seq(), offset: Option[Int] = None,
    limit: Option[Int] = None): Future[Seq[ChatGroup]] = {
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

  /**
   * 根据Class, 获得collection
   * @param cls
   * @tparam T
   * @return
   */
  private def getCollection[T](ds: Datastore, cls: Class[T]): DBCollection = {
    val annotation = cls.getAnnotation(classOf[Property])
    val colName = if (annotation != null)
      annotation.value()
    else
      cls.getSimpleName
    val db = ds.getDB
    db.getCollection(colName)
  }

  // 批量添加讨论组成员
  def addChatGroupMembers(chatGroupId: Long, operatorId: Long, userIdsToAdd: Seq[Long]): Future[Seq[Long]] = {
    // 获得ChatGroup的最大人数
    val futureGroup = getChatGroup(chatGroupId, Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name,
      ChatGroupProp.Avatar, ChatGroupProp.MaxUsers, ChatGroupProp.Participants))

    // 查看是否所有的userId都有效
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    val futureUsers = accountManager.getUsersByIdList(responseFields, None, userIdsToAdd :+ operatorId: _*)

    // 检查users是否都是有效的，并消除Option
    def verifyUsers(users: Map[Long, Option[yunkai.UserInfo]]): Map[Long, yunkai.UserInfo] = {
      if (users exists (_._2.isEmpty))
        throw NotFoundException(Some("Cannot find all the users"))
      users mapValues (_.get)
    }

    // 验证chatGroupId是否有误
    def verify(group: Option[ChatGroup]): ChatGroup = {
      if (group isEmpty)
        throw NotFoundException(Some(s"Cannot find chat group $chatGroupId"))
      else
        group.get
    }

    // 在ChatGroup.participants中添加成员。返回群组中的成员，以及被添加的成员列表。
    def func1(group: ChatGroup, users: Map[Long, yunkai.UserInfo]): Future[(Seq[Long], Seq[yunkai.UserInfo])] = futurePool {
      val col = getCollection(ds, classOf[ChatGroup])
      val usersToAdd = (users map (_._2)).toSeq
      val maxUsers = group.maxUsers
      val fieldName = ChatGroup.fdParticipants
      val addUserCount = usersToAdd.length
      val funcSpec = s"""var l = (this.$fieldName == null) ? 0 : this.$fieldName.length; return l + $addUserCount <= $maxUsers""".stripMargin.trim

      val query = BasicDBObjectBuilder.start().add(ChatGroup.fdChatGroupId, chatGroupId).add("$where", funcSpec).get()
      val fields = BasicDBObjectBuilder.start(Map(ChatGroup.fdParticipants -> 1, ChatGroup.fdMaxUsers -> 1)).get()
      val sort = new BasicDBObject()
      val ops = new BasicDBObject("$addToSet", new BasicDBObject(ChatGroup.fdParticipants, BasicDBObjectBuilder
        .start()
        .add("$each", bufferAsJavaList(userIdsToAdd.toBuffer))
        .add("$slice", maxUsers).get()))

      val doc = col.findAndModify(query, fields, sort, false, ops, true, false)
      if (doc == null)
        throw GroupMembersLimitException()
      else {
        val currentMembers = doc.get(ChatGroup.fdParticipants).asInstanceOf[BasicDBList].toSeq map (_.asInstanceOf[Long])
        currentMembers -> usersToAdd
      }
    }

    for {
      groupOpt <- futureGroup
      group <- Future(verify(groupOpt))
      usersOpt <- futureUsers // Users to be removed(with both userId and nickName available)
      users <- Future(verifyUsers(usersOpt) - operatorId)
      entry <- func1(group, users)
      _ <- emitChatGroupMembersEvents(EventEmitter.evtAddGroupMembers, group, usersOpt(operatorId).get, entry._2)
    } yield {
      entry._1
    }
  }

  /**
   * 发送讨论组添加或删除成员的事件
   * @return
   */
  private def emitChatGroupMembersEvents(eventName: String, group: ChatGroup, operator: yunkai.UserInfo, userList: Seq[yunkai.UserInfo])(implicit futurePool: FuturePool): Future[Unit] = {
    val userInfos = new ObjectMapper().createArrayNode()
    userList foreach (user => {
      val node: JsonNode = user
      userInfos.add(node)
    })

    val eventArgs: Map[String, JsonNode] = Map(
      "chatGroup" -> group,
      "operator" -> operator,
      "targets" -> userInfos
    )
    futurePool {
      EventEmitter.emitEvent(eventName, eventArgs)
    }
  }

  // 批量删除讨论组成员
  def removeChatGroupMembers(chatGroupId: Long, operatorId: Long, userToRemove: Seq[Long]): Future[Seq[Long]] = {
    val responseFields: Seq[UserInfoProp] = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)
    // 查看operatorId是否有效
    val futureOperator = accountManager.getUserById(operatorId, responseFields, None)
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId)
      .retrievedFields(true, ChatGroup.fdParticipants, ChatGroup.fdChatGroupId, ChatGroup.fdName, ChatGroup.fdAvatar)
    val ops = ds.createUpdateOperations(classOf[ChatGroup]).removeAll(ChatGroup.fdParticipants, bufferAsJavaList(userToRemove.toBuffer))
    val groupFuture = futurePool {
      ds.findAndModify(query, ops)
    }

    // 验证chatGroupId是否有误
    def verify(group: ChatGroup): Future[ChatGroup] = {
      if (group == null)
        throw NotFoundException(Some(s"Cannot find chat group $chatGroupId"))
      else
        futurePool {
          group
        }
    }
    // 查看是否所有的userId都有效
    val futureUsers = accountManager.getUsersByIdList(Seq(UserInfoProp.UserId, UserInfoProp.NickName), None, userToRemove: _*)
    val userInfos: Seq[yunkai.UserInfo] = Seq()
    for {
      users <- futureUsers
    } yield {
      for (elem <- users.values.toSeq)
        userInfos.add(elem.get)
    }

    for {
      group <- groupFuture
      group2 <- verify(group)
      operator <- futureOperator
      users <- futureUsers
      _ <- emitChatGroupMembersEvents(EventEmitter.evtRemoveGroupMembers, group2, operator.get, userInfos)
    } yield {
      group.participants
    }
  }

  // 获得讨论组成员
  def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]] = None, selfId: Option[Long]): Future[Seq[yunkai.UserInfo]] = {
    // 取得participants
    def func1(gId: Long): Future[Seq[Long]] = futurePool {
      val groupInfo = ds.find(classOf[ChatGroup], ChatGroup.fdChatGroupId, gId).get()
      if (groupInfo == null)
        throw NotFoundException(Some(s"Cannot find chat group $gId"))
      groupInfo.participants.toSet.toSeq
    }
    // 返回的fields
    val retrievedFields = fields.getOrElse(Seq()) ++ Seq(UserInfoProp.UserId, UserInfoProp.Id)
    // 获取结果
    def func2(fields1: Seq[UserInfoProp], selfId1: Option[Long], members: Seq[Long]): Future[Seq[yunkai.UserInfo]] = {
      val userMap = accountManager.getUsersByIdList(fields1, selfId1, members: _*) map (resultMap => {
        resultMap mapValues (_.orNull)
      })
      userMap map (userList => {
        userList.toSeq map (item => {
          item._2
        })
      })
    }
    for {
      members <- func1(chatGroupId)
      results <- func2(retrievedFields, selfId, members)
    } yield results
  }

  def isMember(userId: Long, chatGroupId: Long): Future[Boolean] = futurePool {
    val query = ds.createQuery(classOf[ChatGroup]).field(ChatGroup.fdChatGroupId).equal(chatGroupId).field(ChatGroup.fdParticipants).hasThisOne(userId)
    query nonEmpty
  }
}
