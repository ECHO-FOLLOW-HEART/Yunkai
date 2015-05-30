package com.aizou.yunkai.model

import javax.validation.constraints.NotNull

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.{ Id, Entity, Indexed }

import scala.beans.BeanProperty
import com.aizou.yunkai.util.Constant

/**
 * Created by pengyt on 2015/5/26.
 */
@Entity
class ChatGroup {
  @BeanProperty
  @Id
  var id: ObjectId = null

  @BeanProperty
  @NotNull
  @Indexed(unique = true)
  var chatGroupId: Long = 0

  @BeanProperty
  var name: String = ""

  @BeanProperty
  var groupDesc: String = Constant.groupDesc

  @BeanProperty
  var groupType: String = ""

  @BeanProperty
  var avatar: String = ""

  @BeanProperty
  var tags: java.util.List[String] = null

  @BeanProperty
  var creator: Long = 0

  @BeanProperty
  var admin: java.util.List[Long] = null

  @BeanProperty
  var participants: java.util.List[Long] = null

  //  @BeanProperty
  //  var participantCnt: Int = 0

  @BeanProperty
  var msgCounter: Long = 0

  @BeanProperty
  var maxUsers: Int = 0

  @BeanProperty
  var createTime: Long = 0

  @BeanProperty
  var updateTime: Long = 0

  @BeanProperty
  var visible: Boolean = true
}
object ChatGroup {
  val fdChatGroupId = "chatGroupId"
  val fdName = "name"
  val fdGroupDesc = "groupDesc"
  val fdGroupType = "groupType"
  val fdTypeCommon = "common"
  val fdAvatar = "avatar"
  val fdTags = "tags"
  val fdCreator = "creator"
  val fdMaxUsers = "maxUsers"
  val fdVisible = "visible"
  val fdParticipants = "participants"
  //val fdParticipantCnt = "participantCnt"
  def apply(creator: Long, chatGroupId: Long, name: String, members: java.util.List[Long]): ChatGroup = {
    val result = new ChatGroup
    result.id = new ObjectId
    result.creator = creator
    result.chatGroupId = chatGroupId
    result.name = name
    result.participants = members
    result
  }
}