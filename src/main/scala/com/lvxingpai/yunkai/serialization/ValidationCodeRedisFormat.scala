package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.lvxingpai.yunkai.Token
import com.lvxingpai.yunkai.model.ValidationCode
import com.redis.serialization.Format

/**
 * Created by zephyre on 7/3/15.
 */
object ValidationCodeRedisFormat {
  def apply() = {
    // 生成相应的object mapper
    val mapper = new ObjectMapper()
    val module = new SimpleModule()
    module.addSerializer(classOf[ValidationCode], new ValidationCodeSerializer())
    module.addSerializer(classOf[Token], new TokenSerializer())
    mapper.registerModule(module)

    val fn: PartialFunction[Any, Any] = {
      case code: ValidationCode => mapper.writeValueAsString(code).getBytes
      case token: Token => mapper.writeValueAsString(token).getBytes
    }

    Format(fn)
  }
}
