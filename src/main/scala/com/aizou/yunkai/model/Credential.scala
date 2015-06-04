package com.aizou.yunkai.model

import javax.validation.constraints.{ Max, Min, NotNull, Size }

import org.bson.types.ObjectId
import org.hibernate.validator.constraints.NotBlank
import org.mongodb.morphia.annotations.{ Entity, Id, Indexed, Property }

import scala.beans.BeanProperty

/**
 * Created by zephyre on 5/20/15.
 */
@Entity
class Credential {
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
  @Size(min = 32, max = 128)
  var salt: String = ""

  @BeanProperty
  @NotBlank
  @Size(min = 32, max = 128)
  @Property("pwdHash")
  var passwdHash: String = ""

  @BeanProperty
  @Min(1)
  @Max(1)
  var method: Int = 1
}

object Credential {
  val fdUserId = "userId"
  val fdSalt = "salt"
  val fdPasswdHash = "passwdHash"

  def apply(userId: Long, salt: String, passwdHash: String): Credential = {
    val result = new Credential
    result.id = new ObjectId
    result.userId = userId
    result.salt = salt
    result.passwdHash = passwdHash
    result
  }
}