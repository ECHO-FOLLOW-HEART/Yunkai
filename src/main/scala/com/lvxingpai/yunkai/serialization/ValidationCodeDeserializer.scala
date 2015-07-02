package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.{JsonNode, DeserializationContext, JsonDeserializer}
import com.lvxingpai.yunkai.model.ValidationCode

/**
 * Created by zephyre on 7/2/15.
 */
class ValidationCodeDeserializer extends JsonDeserializer[ValidationCode] {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): ValidationCode = {
    val node = p.getCodec.readTree[JsonNode](p)
    val code = node.get("code").asText()
    val action = node.get("action").asInt()
    val tel = node.get("tel").asText()
    val createTime = node.get("createTime").asLong()
    val expireTime = node.get("expireTime").asLong()
    val resendTime = node.get("resendTime").asLong()
    val userId = Option(node.get("userId")) map (_.asLong())
    val countryCode = Option(node.get("countryCode")) map (_.asInt())

    ValidationCode(code, action, userId, tel, createTime, expireTime, resendTime, countryCode)
  }
}
