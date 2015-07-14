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

  /**
   * A向B发出了好友请求
   */
  @BeanProperty
  @Embedded
  var reqA: ContactRequest = null

  /**
   * B向A发出了好友请求
   */
  @BeanProperty
  @Embedded
  var reqB: ContactRequest = null

  /**
   * true表示对A而言, B是A的好友; false表示对A而言, B不是A的好友
   */
  @BeanProperty
  var relA: Boolean = false

  /**
   * true表示对B而言, A是B的好友; false表示对B而言, A不是B的好友
   */
  @BeanProperty
  var relB: Boolean = false

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var userA: Long = 0

  @BeanProperty
  @NotNull
  @Min(value = 1)
  var userB: Long = 0

  /**
   * B将A的备注设置为memoA
   */
  @BeanProperty
  var memoA: String = null

  /**
   * A将B的备注设置为memoB
   */
  @BeanProperty
  var memoB: String = null
}

object Relationship {

  val fdId = "id"
  val fdUserA = "userA"
  val fdUserB = "userB"
  val fdMemoA = "memoA"
  val fdMemoB = "memoB"
  val fdReqA = "reqA"
  val fdReqB = "reqB"
  val fdRelA = "relA"
  val fdRelB = "relB"

  def apply(userA: Long, userB: Long, reqA: ContactRequest = null, reqB: ContactRequest = null): Relationship = {
    val result = new Relationship
    result.id = new ObjectId()
    result.userA = userA
    result.userB = userB
    result.reqA = reqA
    result.reqB = reqB
    result
  }
}
