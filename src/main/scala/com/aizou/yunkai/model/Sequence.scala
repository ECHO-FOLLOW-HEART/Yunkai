package com.aizou.yunkai.model

import javax.validation.constraints.{ NotNull }

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.{ Id, Indexed, Entity }

import scala.beans.BeanProperty

/**
 * Created by pengyt on 2015/5/26.
 */
@Entity
class Sequence {
  @BeanProperty
  @Id
  var id: ObjectId = null

  @BeanProperty
  @NotNull
  var column: String = ""

  @BeanProperty
  @NotNull
  @Indexed(unique = true)
  var count: Long = 0
}
object Sequence {
  val groupId: String = "GroupID"
  val userId: String = "UserID"
}