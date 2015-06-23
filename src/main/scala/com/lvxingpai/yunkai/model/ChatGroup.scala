package com.lvxingpai.yunkai.model

import javax.validation.constraints.{Max, Min, NotNull, Size}

import org.bson.types.ObjectId
import org.hibernate.validator.constraints.NotBlank
import org.mongodb.morphia.annotations.{Entity, Id, Indexed}

import scala.beans.BeanProperty

/**
 * Created by pengyt on 2015/5/26.
 */
@Entity
class ChatGroup {
  @BeanProperty
  @Id
  var id: ObjectId = new ObjectId()

  @BeanProperty
  @NotNull
  @Indexed(unique = true)
  var chatGroupId: Long = 0

  @BeanProperty
  @NotBlank
  @Size(min = 2, max = 32)
  var name: String = ""

  @BeanProperty
  @Size(min = 0, max = 1024)
  var groupDesc: String = ""

  //  @BeanProperty
  //  @Size(min = 2, max = 32)
  //  var groupType: String = ""

  @BeanProperty
  @Size(min = 0, max = 128)
  var avatar: String = ""

  @BeanProperty
  var tags: Seq[String] = null

  @BeanProperty
  @NotNull
  var creator: Long = 0

  @BeanProperty
  var admin: Seq[Long] = null

  @BeanProperty
  var participants: Seq[Long] = null

  //  @BeanProperty
  //  var participantCnt: Int = 0

  //  @BeanProperty
  //  var msgCounter: Long = 0

  @BeanProperty
  @NotNull
  @Min(value = 1)
  @Max(value = 2000)
  var maxUsers: Int = 50

  @BeanProperty
  @NotNull
  var createTime: Long = 0

  @BeanProperty
  @NotNull
  var updateTime: Long = 0

  @BeanProperty
  @NotNull
  var visible: Boolean = true
}

object ChatGroup {
  val fdChatGroupId = "chatGroupId"
  val fdName = "name"
  val fdGroupDesc = "groupDesc"
  //  val fdGroupType = "groupType"
  val fdTypeCommon = "common"
  val fdAvatar = "avatar"
  val fdTags = "tags"
  val fdCreator = "creator"
  val fdAdmin = "admin"
  val fdMaxUsers = "maxUsers"
  val fdVisible = "visible"
  val fdParticipants = "participants"
  //val fdParticipantCnt = "participantCnt"

  def apply(creator: Long, chatGroupId: Long, members: Seq[Long]): ChatGroup = {
    val result = new ChatGroup
    result.id = new ObjectId
    result.creator = creator
    result.admin = Seq(creator)
    result.chatGroupId = chatGroupId
    result.participants = members
    result
  }
}