package com.aizou.yunkai.model

import javax.validation.constraints.{ Min, NotNull }

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.{ Indexed, Id, Entity }

import scala.beans.BeanProperty

/**
 * Created by pengyt on 2015/5/26.
 */
@Entity
class Message {
  @BeanProperty
  @Id
  var conversation: ObjectId = null

  @BeanProperty
  @NotNull
  @Min(1)
  @Indexed(unique = true)
  var msgId: Long = 0

  @BeanProperty
  @NotNull
  var senderId: Long = 0

  @BeanProperty
  @NotNull
  var receiverId: Long = 0

  @BeanProperty
  var senderName: String = ""

  @BeanProperty
  var chatType: String = ""

  @BeanProperty
  var senderAvatar: String = ""

  @BeanProperty
  var contents: String = ""

  @BeanProperty
  var msgType: Int = 0

  @BeanProperty
  var timestamp: Long = 0
}
