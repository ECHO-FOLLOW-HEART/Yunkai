package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ SerializerProvider, JsonSerializer }
import com.lvxingpai.yunkai.Token
import scala.language.postfixOps

/**
 * Created by zephyre on 7/4/15.
 */
class TokenSerializer extends JsonSerializer[Token] {
  override def serialize(value: Token, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    gen.writeStringField("fingerprint", value.fingerprint)
    gen.writeNumberField("action", value.action.value)

    val tel = value.tel
    if (tel isEmpty)
      gen.writeNullField("tel")
    else
      gen.writeStringField("tel", tel.get)

    gen.writeNumberField("createTime", value.createTime)

    val userId = value.userId
    if (userId isEmpty)
      gen.writeNullField("userId")
    else
      gen.writeNumberField("userId", userId.get)

    val countryCode = value.countryCode
    if (countryCode isEmpty)
      gen.writeNullField("countryCode")
    else
      gen.writeNumberField("countryCode", countryCode.get)

    gen.writeEndObject()
  }
}
