package com.lvxingpai.yunkai.formatter

import javax.inject.{ Inject, Singleton }

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.lvxingpai.yunkai.UserInfo

/**
 * Created by zephyre on 1/15/16.
 */
@Singleton
class UserInfoFormatter @Inject() extends BaseFormatter {
  override protected val objectMapper: ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val module = new SimpleModule()
    module.addSerializer(classOf[UserInfo], new UserInfoSerializer)

    mapper.registerModule(module)
    mapper
  }
}
