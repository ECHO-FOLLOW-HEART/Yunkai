package com.lvxingpai.yunkai.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.node.{ NullNode, NumericNode, TextNode }
import com.fasterxml.jackson.databind.{ DeserializationContext, JsonDeserializer, JsonNode }
import com.lvxingpai.yunkai.{ OperationCode, Token }

/**
 * Created by zephyre on 7/4/15.
 */
class TokenDeserializer extends JsonDeserializer[Token] {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): Token = {
    val node = p.getCodec.readTree[JsonNode](p)

    val fp = node.get("fingerprint").asText()
    val action = node.get("action").asInt() match {
      case item if item == OperationCode.Signup.value => OperationCode.Signup
      case item if item == OperationCode.ResetPassword.value => OperationCode.ResetPassword
      case item if item == OperationCode.UpdateTel.value => OperationCode.UpdateTel
    }

    val createTime = node.get("createTime").asLong()

    val tel = node.get("tel") match {
      case _: NullNode => None
      case item: TextNode => Some(item.asText())
    }
    val userId = node.get("userId") match {
      case _: NullNode => None
      case item: NumericNode => Some(item.asLong())
    }
    val countryCode = node.get("countryCode") match {
      case _: NullNode => None
      case item: NumericNode => Some(item.asInt())
    }

    Token(fp, action, userId, countryCode, tel, createTime)
  }
}
