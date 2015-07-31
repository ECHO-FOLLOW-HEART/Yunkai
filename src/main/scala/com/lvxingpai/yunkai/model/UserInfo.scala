package com.lvxingpai.yunkai.model

import java.util.{ List => JList, UUID }
import javax.validation.constraints.{ Min, NotNull, Size }

import org.bson.types.ObjectId
import org.hibernate.validator.constraints.NotBlank
import org.mongodb.morphia.annotations.{ Entity, Indexed, Version, Transient }

import scala.beans.BeanProperty

/**
 * Created by zephyre on 5/4/15.
 */
@Entity
class UserInfo extends AbstractEntity {
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
  var avatar: String = null

  @BeanProperty
  @Size(max = 1)
  var gender: String = null

  @BeanProperty
  @Indexed(unique = true)
  @Size(min = 6, max = 32)
  var tel: String = null

  //  @BeanProperty
  //  var contacts: Seq[Long] = null

  //  @BeanProperty
  //  var chatGroups: JList[Long] = null

  @BeanProperty
  @NotNull
  var loginStatus: Boolean = false

  @BeanProperty
  @NotNull
  var loginTime: Long = 0

  @BeanProperty
  @NotNull
  var logoutTime: Long = 0

  @BeanProperty
  var loginSource: JList[String] = null

  @Version
  var version: Long = 0

  @BeanProperty
  var roles: JList[Int] = null

  @Transient
  var memo: String = null

  var oauthIdList: JList[String] = null
  @BeanProperty
  var oauthInfoList: JList[OAuthInfo] = null
}

object UserInfo {
  val fdId = "id"
  val fdUserId = "userId"
  val fdNickName = "nickName"
  val fdAvatar = "avatar"
  val fdContacts = "contacts"
  val fdSignature = "signature"
  val fdTel = "tel"
  val fdGender = "gender"
  val fdChatGroups = "chatGroups"
  val fdRoles = "roles"
  val fdOauthInfoList = "oauthInfoList"
  val fdOauthIdList = "oauthIdList"
  val fdLoginStatus = "loginStatus"
  val fdLoginTime = "loginTime"
  val fdLogoutTime = "logoutTime"
  val fdLoginSource = "loginSource"

  def apply(userId: Long, nickName: String): UserInfo = {
    val result = new UserInfo
    result.id = new ObjectId
    result.userId = userId
    result.nickName = nickName
    result.tel = UUID.randomUUID().toString
    result
  }
}

