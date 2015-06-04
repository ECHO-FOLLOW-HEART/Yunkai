package com.aizou.yunkai.model

import javax.validation.constraints.{ Min, NotNull }

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations._

import scala.beans.BeanProperty

/**
 * Created by zephyre on 5/5/15.
 */
@Entity
@Indexes(Array(new Index(fields = Array(new Field("userA"), new Field("userB")), options = new IndexOptions(unique = true))))
class Relationship {
  @BeanProperty
  @Id
  var id: ObjectId = null

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var userA: Long = 0

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var userB: Long = 0

  @BeanProperty
  var memoA: String = null

  @BeanProperty
  var memoB: String = null
}

object Relationship {
  val fdUserA = "userA"
  val fdUserB = "userB"

  def apply(userA: Long, userB: Long): Relationship = {
    val result = new Relationship
    result.userA = userA
    result.userB = userB
    result
  }
}
