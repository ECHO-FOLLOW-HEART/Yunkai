package com.lvxingpai.yunkai.model

import java.security.MessageDigest

import com.lvxingpai.yunkai.OperationCode

import scala.language.postfixOps

/**
 * action: 1: 新建用户
 *
 * Created by zephyre on 7/2/15.
 */
case class ValidationCode(code: String, action: OperationCode, userId: Option[Long] = None, tel: String,
    countryCode: Option[Int], createTime: Long = System.currentTimeMillis,
    var checked: Boolean = false) {
  val fingerprint: String = ValidationCode.calcFingerprint(action, tel, countryCode)
}

object ValidationCode {
  /**
   * 用来确定一个验证码对象（即验证码对象在数据库中的主键）。它由下面的算法生成：
   * 生成字符串：
   * 如果提供了userId，则：action + userId
   * 如果未提供userId，则：action + countryCode + tel
   * 否则报错：InvalidArgsException
   */
  def calcFingerprint(action: OperationCode, tel: String, countryCode: Option[Int] = None): String = {
    val plain = s"action=${action.value}&tel=00${countryCode.getOrElse(86)}$tel"
    val bytes = MessageDigest.getInstance("MD5").digest(plain.getBytes)
    bytes map ("%02x" format _) mkString
  }

  def calcRedisKey(action: OperationCode, tel: String, countryCode: Option[Int] = None): String = {
    val fingerprint = calcFingerprint(action, tel, countryCode)
    s"yunkai:valcode/$fingerprint"
  }
}
