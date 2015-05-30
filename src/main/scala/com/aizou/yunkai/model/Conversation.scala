package com.aizou.yunkai.model

import javax.validation.constraints.NotNull

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.{ Id, Indexed, Entity }

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
  @Indexed(unique = true)
  var fingerprint: String = ""

  @BeanProperty
  @NotNull
  @Indexed(unique = true)
  var creator: Long = 0

  @BeanProperty
  var admin: Long = 0

  @BeanProperty
  var participants: java.util.List[Long] = null

  @BeanProperty
  var msgCounter: Long = 0

  @BeanProperty
  var createTime: Long = 0

  @BeanProperty
  var updateTime: Long = 0
}
object Conversation {
  val fdParticipants = "participants"
  val fdFingerprint = "fingerprint"
  def apply(userA: Long, userB: Long): Conversation = {
    val result = new Conversation
    val l = Seq(userA, userB).sorted
    result.setId(new ObjectId())
    result.setParticipants(java.util.Arrays.asList(l head, l last))
    //result.setFingerprint(String.format("%d.%d", l head, l last))
    result.setCreateTime(java.lang.System.currentTimeMillis())
    result.setUpdateTime(java.lang.System.currentTimeMillis())
    result
  }
}
