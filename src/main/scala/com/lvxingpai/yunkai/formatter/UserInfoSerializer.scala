package com.lvxingpai.yunkai.formatter

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ SerializerProvider, JsonSerializer }
import com.lvxingpai.yunkai.UserInfo

/**
 * Created by zephyre on 1/15/16.
 */
class UserInfoSerializer extends JsonSerializer[UserInfo] {
  override def serialize(user: UserInfo, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()

    gen.writeStringField("id", user.id)
    gen.writeNumberField("userId", user.userId)
    gen.writeStringField("nickname", user.nickName)
    gen.writeStringField("avatar", user.avatar getOrElse "")

    gen.writeEndObject()
  }
}
