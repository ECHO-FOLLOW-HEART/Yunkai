package com.lvxingpai.yunkai.utils

/**
 * Created by topy on 2015/7/28.
 */
object RequestUtils {

  def getWeiXinUrl(code: String): String = {
    val wxAppId = "wx86048e56adaf7486"
    val wxSecret = "d5408e689b82c0335a728cc8bd1b3c2e"
    val wxDomain = "api.weixin.qq.com"
    val wxAccess = "/sns/oauth2/access_token"
    val wxUserInfo = "/sns/userinfo"

    val result = "https://" + wxDomain + wxAccess + "?appid=" + wxAppId + "&secret=" + wxSecret + "&code=" + code + "&grant_type=authorization_code"
    result
  }


}
