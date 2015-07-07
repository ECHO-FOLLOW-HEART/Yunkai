package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.lvxingpai.yunkai.Token
import com.redis.serialization.Parse

/**
 * Created by zephyre on 7/4/15.
 */
object TokenRedisParse {
  def apply() = {
    Parse[Token](bytes => {
      val contents = new String(bytes)

      // 生成相应的object mapper
      val mapper = new ObjectMapper()
      val module = new SimpleModule()
      module.addDeserializer(classOf[Token], new TokenDeserializer())
      mapper.registerModule(module)

      mapper.readValue(contents, classOf[Token])
    })
  }
}
