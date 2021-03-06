package com.lvxingpai.yunkai.model

import javax.validation.constraints.{ Max, Min, NotNull }

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations._

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
  var salt: String = ""

  @BeanProperty
  @Property("pwdHash")
  var passwdHash: String = ""

  @BeanProperty
  @Min(1)
  @Max(1)
  var method: Int = 1

  @Embedded
  @NotNull
  var secretKey: SecretKey = _
}

object Credential {
  val fdUserId = "userId"
  val fdSalt = "salt"
  val fdPasswdHash = "passwdHash"

  def apply(userId: Long, salt: String, passwdHash: String, secretKey: SecretKey): Credential = {
    val result = new Credential
    result.id = new ObjectId
    result.userId = userId
    result.salt = salt
    result.passwdHash = passwdHash
    result.secretKey = secretKey
    result
  }
}