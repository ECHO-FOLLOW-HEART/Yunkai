package com.lvxingpai.yunkai.model

import javax.validation.constraints.NotNull
import org.mongodb.morphia.annotations.{ Indexed, Embedded }
/**
 * Created by pengyt on 2015/7/28.
 */
@Embedded
class OAuthInfo {
  /**
   * 第三方账号体系的名称。比如：weixin表示这是微信账号
   */
  @NotNull
  var provider: String = ""

  /**
   * 用户在第三方账号体系中的id
   */
  @Indexed(unique = true)
  @NotNull
  var oauthId: String = ""

  /**
   * 用户在第三方账号的昵称
   */
  @NotNull
  var nickName: String = ""

  //  /**
  //   * 用户在第三方账号的头像
  //   */
  //  var avatar: String = null

  /**
   * 用户在第三方账号的token
   */
  var token: String = null
}
object OAuthInfo {
  def apply(provider: String, oauthId: String, nickName: String): OAuthInfo = {
    val result = new OAuthInfo
    result.provider = provider
    result.oauthId = oauthId
    result.nickName = nickName
    result
  }
}