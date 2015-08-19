package com.lvxingpai.yunkai.model

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
  var contactA: Boolean = false

  @BeanProperty
  var contactB: Boolean = false

  @BeanProperty
  var memoA: String = null

  @BeanProperty
  var memoB: String = null

  @BeanProperty
  var blockA: Boolean = false

  @BeanProperty
  var blockB: Boolean = false
}

object Relationship {

  val fdId = "id"
  val fdUserA = "userA"
  val fdUserB = "userB"
  val fdMemoA = "memoA"
  val fdMemoB = "memoB"
  val fdBlockA = "blockA"
  val fdBlockB = "blockB"
  val fdContactA = "contactA"
  val fdContactB = "contactB"

  def apply(userA: Long, userB: Long): Relationship = {
    val result = new Relationship
    result.userA = userA
    result.userB = userB
    result
  }
}
