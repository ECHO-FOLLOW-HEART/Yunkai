package com.lvxingpai.yunkai

import java.util.{List => JList}

import com.fasterxml.jackson.databind.node.{LongNode, TextNode}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.twitter.util.FuturePool

import scala.collection.JavaConversions._
import scala.language.implicitConversions

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit lazy val mongoDatastore = MorphiaFactory.datastore

  implicit lazy val defaultFuturePool = FuturePool.unboundedPool

  implicit lazy val defaultExecutionContext = scala.concurrent.ExecutionContext
    .fromExecutorService(defaultFuturePool.executor)

  object JsonConversions {
    implicit def string2JsonNode(text: String): JsonNode = TextNode.valueOf(Option(text) getOrElse "")

    implicit def long2JsonNode(value: Long): JsonNode = LongNode.valueOf(value)

    implicit def int2JsonNode(value: Int): JsonNode = LongNode.valueOf(value)

    implicit def jlist2JsonNode[T](list: JList[T]): JsonNode = new ObjectMapper().valueToTree(list)

    implicit def seq2JsonNode[T](seq: Seq[T]): JsonNode = new ObjectMapper().valueToTree(seqAsJavaList(seq))

    implicit def JsonNode2string(node: JsonNode): String = new ObjectMapper().writeValueAsString(node)
  }

}
