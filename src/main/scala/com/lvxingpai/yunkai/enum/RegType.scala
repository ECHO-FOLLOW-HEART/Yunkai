package com.lvxingpai.yunkai.enum

/**
 * 用户注册的类型
 *
 * Created by zephyre on 11/13/15.
 */
object RegType extends Enumeration {
  // 通过手机号码注册
  val Tel = Value("tel")

  // 通过email注册
  val Email = Value("email")
}
