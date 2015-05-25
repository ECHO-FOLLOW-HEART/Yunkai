package com.aizou.yunkai.model

import javax.validation.constraints.{Max, Min, NotNull, Size}

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.{Entity, Id, Version}

import scala.beans.BeanProperty
import scala.collection.JavaConversions._

/**
 * 普通讨论组
 * Created by zephyre on 5/15/15.
 */
@Entity
class ChatGroup {
  @BeanProperty
  @Id
  var id: ObjectId = new ObjectId()

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var groupId: Long = 0

  @BeanProperty
  @NotNull
  @Size(min = 2, max = 32)
  var name: String = null

  @BeanProperty
  @Size(min = 2, max = 1024)
  var desc: String = null

  @BeanProperty
  @Size(min = 32, max = 32)
  var avatar: String = null

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var creator: Long = 0

  @BeanProperty
  @Size(max = 64)
  var admin: java.util.List[Long] = null

  @BeanProperty
  @Size(min = 1, max = 1024)
  var members: java.util.List[Long] = null

  @BeanProperty
  @NotNull
  @Min(value = 1)
  @Max(value = 1024)
  var maxMembers: Int = 50

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var createTime: Long = 0

  @BeanProperty
  @Version
  var version: Long = 0
}

object ChatGroup {
  def apply(groupId: Long, name: String, members: Seq[Long], maxMembers: Int = 50): ChatGroup = {
    val group = new ChatGroup
    group.groupId = groupId
    group.name = name
    group.members = members
    group.maxMembers = maxMembers
    group.createTime = System.currentTimeMillis()
    group
  }
}
