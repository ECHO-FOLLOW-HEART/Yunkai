package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ JsonSerializer, SerializerProvider }
import com.lvxingpai.yunkai.model.ValidationCode

import scala.language.postfixOps

/**
 * Created by zephyre on 7/2/15.
 */
class ValidationCodeSerializer extends JsonSerializer[ValidationCode] {
  override def serialize(value: ValidationCode, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    gen.writeStringField("code", value.code)
    gen.writeNumberField("action", value.action.value)
    gen.writeStringField("tel", value.tel)
    gen.writeNumberField("createTime", value.createTime)
    gen.writeBooleanField("checked", value.checked)

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
