package com.aizou.yunkai.model

import javax.validation.constraints.{ NotNull, Size }

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.{ Entity, Id, Indexed }

import scala.beans.BeanProperty

/**
 * Created by pengyt on 2015/5/26.
 */
@Entity
class Conversation {
  @BeanProperty
  @Id
  var id: ObjectId = null

  @BeanProperty
  @Size(min = 1, max = 64)
  @Indexed(unique = true)
  var fingerprint: String = ""

  //  @BeanProperty
  //  @NotNull
  //  @Indexed(unique = true)
  //  var creator: Long = 0

  //  @BeanProperty
  //  @NotNull
  //  var admin: Long = 0

  @BeanProperty
  var participants: Seq[Long] = null

  @BeanProperty
  var msgCounter: Long = 0

  @BeanProperty
  @NotNull
  var createTime: Long = 0

  @BeanProperty
  @NotNull
  var updateTime: Long = 0
}

object Conversation {
  val fdParticipants = "participants"
  val fdFingerprint = "fingerprint"
  val fdId = "id"

  def apply(userA: Long, userB: Long): Conversation = {
    val result = new Conversation
    val l = Seq(userA, userB).sorted
    result.id = new ObjectId()
    result.participants = Seq(l head, l last)
    //result.fingerprint = String.format("%d.%d", l head, l last)
    result.createTime = System.currentTimeMillis()
    result.updateTime = System.currentTimeMillis()
    result
  }
}
