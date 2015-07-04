package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.node.{NullNode, NumericNode}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonNode}
import com.lvxingpai.yunkai.OperationCode
import com.lvxingpai.yunkai.model.ValidationCode

/**
 * Created by zephyre on 7/2/15.
 */
class ValidationCodeDeserializer extends JsonDeserializer[ValidationCode] {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): ValidationCode = {
    val node = p.getCodec.readTree[JsonNode](p)
    val code = node.get("code").asText()
    val action = node.get("action").asInt() match {
      case item if item == OperationCode.Signup.value => OperationCode.Signup
      case item if item == OperationCode.ResetPassword.value => OperationCode.ResetPassword
      case item if item == OperationCode.UpdateTel.value => OperationCode.UpdateTel
    }

    val tel = node.get("tel").asText()
    val createTime = node.get("createTime").asLong()
    val checked = node.get("checked").asBoolean()
    val userId =
      node.get("userId") match {
        case _: NullNode => None
        case item: NumericNode => Some(item.asLong())
      }
    val countryCode = node.get("countryCode") match {
      case _: NullNode => None
      case item: NumericNode => Some(item.asInt())
    }

    ValidationCode(code, action, userId, tel, countryCode, createTime, checked)
  }
}
