package com.lvxingpai.yunkai

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.typesafe.config.{ Config, ConfigFactory }
import dispatch.{ Http, url }
import scala.language.postfixOps
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

/**
 * Created by zephyre on 5/18/15.
 */
object AppConfig {
  val conf = buildConfig()

  // 获得数据库的配置。主要的键有两个：host和port
  private def getDatabaseConf(service: (String, String)): Future[java.util.Map[String, Object]] = {
    val serviceName = service._1
    val confKey = service._2

    val page = url(s"http://etcd:2379/v2/keys/backends/$serviceName?recursive=true")
    val response = Http(page OK dispatch.as.String)

    response map (body => {
      val mapper = new ObjectMapper() with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      val confNode = mapper.readValue[JsonNode](body)

      val dbConf = confNode.get("node").get("nodes").head.get("value").asText()
      val tmp = dbConf.split(":")
      val host = tmp(0)
      val port = Int.box(tmp(1).toInt)

      val innerMap = new java.util.HashMap[String, Object]()
      innerMap.put("host", host)
      innerMap.put("port", port)

      val m = new java.util.HashMap[String, Object]()
      m.put(confKey, innerMap)
      m
    })
  }

  private def buildConfNode(node: JsonNode): java.util.Map[String, Object] = {
    // 是否为dir类型的键
    def isDir(rootNode: JsonNode): Boolean = {
      val dirNode = node.get("dir")
      dirNode != null && dirNode.asBoolean()
    }

    // 获得key
    def getKeyName(rootNode: JsonNode): String = {
      val keyStr = rootNode.get("key").asText()
      (keyStr split "/").last
    }

    if (isDir(node)) {
      val innerMap = new java.util.HashMap[String, Object]()
      for (item <- node.get("nodes")) {
        val item2 = buildConfNode(item)
        for (entrySet <- item2.entrySet())
          innerMap.put(entrySet.getKey, entrySet.getValue)
      }

      val key = getKeyName(node)
      val m = new java.util.HashMap[String, Object]()
      m.put(key, innerMap)
      m
    } else {
      val key = getKeyName(node)
      val valueNode = node.get("value")

      val value = if (valueNode.canConvertToInt)
        Int.box(valueNode.asInt())
      else if (valueNode.canConvertToLong)
        Long.box(valueNode.asLong())
      else if (valueNode.isDouble)
        Double.box(valueNode.asDouble())
      else if (valueNode.isTextual) {
        val rawVal = valueNode.asText()
        try {
          Int.box(rawVal.toInt)
        } catch {
          case _: NumberFormatException => try {
            Long.box(rawVal.toLong)
          } catch {
            case _: NumberFormatException => try {
              Double.box(rawVal.toDouble)
            } catch {
              case _: NumberFormatException => rawVal
            }
          }
        }
      } else if (valueNode.isBoolean)
        Boolean.box(valueNode.asBoolean())
      else if (valueNode.isNull)
        null
      else
        throw new IllegalArgumentException

      val m = new java.util.HashMap[String, Object]()
      m.put(key, value)
      m
    }
  }

  // 从etcd数据库获取配置数据
  private def buildConfig(): Config = {
    val page = url("http://etcd:2379/v2/keys/project-conf/yunkai?recursive=true")
    val response = Http(page OK dispatch.as.String)
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    val confNode = mapper.readValue[JsonNode](Await.result(response, 10 seconds))

    val confList = for {
      confEntry <- confNode.get("node").get("nodes")
      conf <- buildConfNode(confEntry)
    } yield conf

    val configMap = Map(confList.toSeq: _*)

    val confMain = ConfigFactory.parseMap(configMap)

    val sideConf = Future.sequence(Seq(getDatabaseConf("redis" -> "redis"), getDatabaseConf("mongo" -> "mongo")))

    val result = Await.result(sideConf, 10 seconds).reduce(_ ++ _)

    val config = ConfigFactory.load()

    ConfigFactory.parseMap(result).withFallback(confMain).withFallback(config)
  }

}
