package com.aizou.yunkai.model

import javax.validation.constraints.{ Min, NotNull, Size }

import org.bson.types.ObjectId
import org.hibernate.validator.constraints.NotBlank
import org.mongodb.morphia.annotations.{ Entity, Id, Version }

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
  var userId: Long = 0

  @BeanProperty
  @NotBlank
  @Size(min = 2, max = 32)
  var nickName: String = ""

  @BeanProperty
  var avatar: String = ""

  @BeanProperty
  var contacts: java.util.List[Int] = null

  @Version
  var version: Long = 0
}

object UserInfo {
  def apply(userId: Long, nickName: String, avatar: String): UserInfo = {
    val result = new UserInfo
    result.setId(new ObjectId)
    result.setUserId(userId)
    result.setNickName(nickName)
    result.setAvatar(avatar)
    result
  }
}