package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.lvxingpai.yunkai.model.ValidationCode
import com.redis.serialization.Parse

/**
 * Created by zephyre on 7/3/15.
 */
object ValidationCodeRedisParse {

  def apply() = {
    Parse[ValidationCode](bytes => {
      val contents = new String(bytes)

      // 生成相应的object mapper
      val mapper = new ObjectMapper()
      val module = new SimpleModule()
      module.addDeserializer(classOf[ValidationCode], new ValidationCodeDeserializer())
      mapper.registerModule(module)

      mapper.readValue(contents, classOf[ValidationCode])
    })
  }
}
