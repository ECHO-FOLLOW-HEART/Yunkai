package com.lvxingpai.yunkai.model

import javax.validation.constraints.{ Min, NotNull, Size }

import org.bson.types.ObjectId
import org.hibernate.validator.constraints.NotBlank
import org.mongodb.morphia.annotations.{ Entity, Id, Indexed, Version }

import scala.beans.BeanProperty

/**
 * Created by zephyre on 5/4/15.
 */
@Entity
class UserInfo {
  @BeanProperty
  @Id
  var id: ObjectId = null

  @BeanProperty
  @NotNull
  @Min(value = 1)
  @Indexed(unique = true)
  var userId: Long = 0

  @BeanProperty
  @NotBlank
  @Size(min = 2, max = 32)
  var nickName: String = ""

  @BeanProperty
  @Size(min = 2, max = 512)
  var signature: String = null

  @BeanProperty
  var avatar: String = ""

  @BeanProperty
  @Size(min = 6, max = 11)
  var tel: String = ""

  @BeanProperty
  var contacts: Seq[Int] = null

  @Version
  var version: Long = 0
}

object UserInfo {

  val fdUserId = "userId"
  val fdNickName = "nickName"
  val fdAvatar = "avatar"
  val fdContacts = "contacts"
  val fdSignature = "signature"
  val fdTel = "tel"

  def apply(userId: Long, nickName: String): UserInfo = {
    val result = new UserInfo
    result.id = new ObjectId
    result.userId = userId
    result.nickName = nickName
    result
  }
}

