package com.lvxingpai.yunkai.model

import java.security.MessageDigest

import scala.language.postfixOps

/**
 * action: 1: 新建用户
 *
 * Created by zephyre on 7/2/15.
 */
case class ValidationCode(code: String, action: Int, userId: Option[Long], tel: String, createTime: Long,
                          expireTime: Long, resendTime: Long, countryCode: Option[Int]) {
  object ActionCode extends Enumeration {
    val SIGNUP = Value(1)
  }

  def fingerprint: String = {
    val u = userId getOrElse ""
    val cd = countryCode getOrElse 86
    val plain = s"action=$action&user=$u&tel=$tel&country=$cd"
    val bytes = MessageDigest.getInstance("MD5").digest(plain.getBytes)
    bytes map ("%02x" format _) mkString
  }
}

object ValidationCode {
  def apply(code: String, action: Int, userId: Option[Long], tel: String, expire: Long, resendInterval: Long,
            countryCode: Option[Int]): ValidationCode = {
    val current = System.currentTimeMillis
    val expireTime = current + expire
    val resendTime = current + resendInterval
    ValidationCode(code, action, userId, tel, current, expireTime, resendTime, countryCode)
  }
}
