package com.lvxingpai.yunkai.handler

import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.{ UserInfoProp, ChatGroupProp }
import com.lvxingpai.yunkai.model.ChatGroup
import com.twitter.util.Future

/**
 * Created by pengyt on 2015/9/18.
 */
trait IGroupManager {

  def getUserChatGroupCount(userId: Long): Future[Int]

  def createChatGroup(creator: Long, members: Seq[Long], chatGroupProps: Map[ChatGroupProp, Any] = Map()): Future[ChatGroup]

  def getChatGroup(chatGroupId: Long, fields: Seq[ChatGroupProp] = Seq[ChatGroupProp]()): Future[Option[ChatGroup]]

  def getChatGroups(fields: Seq[ChatGroupProp], groupIdList: Long*): Future[Map[Long, Option[ChatGroup]]]

  def updateChatGroup(chatGroupId: Long, operatorId: Long, chatGroupProps: Map[ChatGroupProp, Any]): Future[Option[ChatGroup]]

  def getUserChatGroups(userId: Long, fields: Seq[ChatGroupProp] = Seq(), offset: Option[Int] = None,
    limit: Option[Int] = None): Future[Seq[ChatGroup]]

  def addChatGroupMembers(chatGroupId: Long, operatorId: Long, userIdsToAdd: Seq[Long]): Future[Seq[Long]]

  def removeChatGroupMembers(chatGroupId: Long, operatorId: Long, userToRemove: Seq[Long]): Future[Seq[Long]]

  def getChatGroupMembers(chatGroupId: Long, fields: Option[Seq[UserInfoProp]] = None, selfId: Option[Long]): Future[Seq[yunkai.UserInfo]]

  def isMember(userId: Long, chatGroupId: Long): Future[Boolean]
}
