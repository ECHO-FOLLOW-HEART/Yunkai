package com.lvxingpai.yunkai.model

import java.lang.{Integer => JInt, Long => JLong}
import javax.validation.constraints.{Max, Min, NotNull, Size}

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations._

import scala.beans.BeanProperty

/**
 * Created by zephyre on 6/25/15.
 */
@Entity
@Indexes(Array(new Index(fields=Array(new Field("sender"), new Field("receiver")), options = new IndexOptions(unique = true))))
class ContactRequest {

  @BeanProperty
  @Id
  var id: ObjectId = new ObjectId()

  /**
   * 发起邀请的用户
   */
  @BeanProperty
  @NotNull
  @Min(value = 1)
  var sender: Long = 0

  /**
   * 被邀请用户
   */
  @BeanProperty
  @NotNull
  @Min(value = 1)
  var receiver: Long = 0

  /**
   * 发起邀请的时间
   */
  @BeanProperty
  @Min(value = 0)
  @NotNull
  var timestamp: JLong = null

  /**
   * 过期时间
   */
  @BeanProperty
  @Min(value = 0)
  @NotNull
  @Indexed()
  var expire: JLong = null

  /**
   * 邀请的状态。有：等待接受/已同意/已拒绝/已撤销这几种。参见RequestStatus
   */
  @BeanProperty
  @Min(value = 0)
  @Max(value = 3)
  var status: JInt = null

  /**
   * 邀请的附言
   */
  @BeanProperty
  @Size(max = 400)
  var requestMessage: String = null

  /**
   * 拒绝的附言
   */
  @BeanProperty
  @Size(max = 400)
  var rejectMessage: String = null
}


object ContactRequest {

  def apply(sender: Long, receiver: Long, message: Option[String], expire: Option[Long]): ContactRequest = {
    val req = new ContactRequest
    req.sender = sender
    req.receiver = receiver
    req.requestMessage = message.orNull
    req.status = ContactRequest.RequestStatus.PENDING.id
    // 默认情况下，请求七天后过期
    val defaultExpireDelay = 7 * 24 * 3600 * 1000L
    val current = System.currentTimeMillis()
    req.expire = current + expire.getOrElse(defaultExpireDelay)
    req.timestamp = current

    req
  }

  object RequestStatus extends Enumeration {
    val PENDING, ACCEPTED, REJECTED, CANCELLED = Value
  }

  val fdContactRequestId: String = "id"
  val fdSender: String = "sender"
  val fdReceiver: String = "receiver"
  val fdTimestamp: String = "timestamp"
  val fdExpire: String = "expire"
  val fdStatus: String = "status"
  val fdRequestMessage: String = "requestMessage"
  val fdRejectMessage: String = "rejectMessage"
}
