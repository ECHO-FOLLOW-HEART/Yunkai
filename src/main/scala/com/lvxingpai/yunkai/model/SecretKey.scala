package com.lvxingpai.yunkai.model

import java.util.{ Date, UUID }
import javax.validation.constraints.NotNull

import org.hibernate.validator.constraints.{ Length, NotBlank }
import org.mongodb.morphia.annotations.Embedded

/**
 * 用户的SecretKey, 可以用作client-side authentication
 * Created by zephyre on 1/8/16.
 */
@Embedded
class SecretKey {
  /**
   * key的内容
   */
  @NotBlank
  @Length(min = 32, max = 256)
  var key: String = _

  /**
   * key的生成时间
   */
  @NotNull
  var timestamp: Date = _

  /**
   * key的过期时间
   */
  var expire: Date = _
}

object SecretKey {
  def apply(): SecretKey = {
    val result = new SecretKey

    result.key = org.apache.commons.codec.digest.DigestUtils.sha256Hex(UUID.randomUUID().toString)
    result.timestamp = new Date()

    result
  }
}