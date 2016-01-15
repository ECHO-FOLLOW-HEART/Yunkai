package com.lvxingpai.yunkai.formatter

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }

import scala.reflect.ClassTag
import scala.reflect._

/**
 * Created by pengyt on 2015/8/28.
 */
trait BaseFormatter {
  protected val objectMapper: ObjectMapper

  def formatJsonNode(obj: AnyRef): JsonNode = objectMapper.valueToTree(obj)

  def formatString(obj: AnyRef): String = objectMapper.writeValueAsString(obj)

  def parse[T: ClassTag](contents: String): T = objectMapper.readValue(
    contents,
    classTag[T].runtimeClass
  ).asInstanceOf[T]
}

