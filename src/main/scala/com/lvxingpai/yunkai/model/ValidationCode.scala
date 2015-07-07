package com.lvxingpai.yunkai.model

import java.security.MessageDigest

import com.lvxingpai.yunkai.{InvalidArgsException, OperationCode}

import scala.language.postfixOps

/**
 * action: 1: 新建用户
 *
 * Created by zephyre on 7/2/15.
 */
case class ValidationCode(code: String, action: OperationCode, userId: Option[Long] = None, tel: String,
                          countryCode: Option[Int], createTime: Long = System.currentTimeMillis,
                          var checked: Boolean = false) {
  val fingerprint: String = ValidationCode.calcFingerprint(action, userId, Some(tel), countryCode)
}

object ValidationCode {
  /**
   * 用来确定一个验证码对象（即验证码对象在数据库中的主键）。它由下面的算法生成：
   * 生成字符串：
   * 如果提供了userId，则：action + userId
   * 如果未提供userId，则：action + countryCode + tel
   * 否则报错：InvalidArgsException
   */
  def calcFingerprint(action: OperationCode, userId: Option[Long] = None, tel: Option[String] = None, countryCode: Option[Int] = None): String = {
    val track = try {
      userId getOrElse s"00${countryCode.getOrElse(86)}${tel.get}"
    } catch {
      case _: NoSuchElementException =>
        throw InvalidArgsException()
    }
    val plain = s"action=${action.value}&track=$track"
    val bytes = MessageDigest.getInstance("MD5").digest(plain.getBytes)
    bytes map ("%02x" format _) mkString
  }

  def calcRedisKey(action: OperationCode, userId: Option[Long] = None, tel: Option[String] = None, countryCode: Option[Int] = None): String = {
    val fingerprint = calcFingerprint(action, userId, tel, countryCode)
    s"yunkai:valcode/$fingerprint"
  }
}
