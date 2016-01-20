package com.lvxingpai.yunkai.service

import javax.inject.{ Inject, Named }

import com.fasterxml.jackson.databind.ObjectMapper
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.yunkai.Implicits
import com.twitter.util.Future

import scala.util.Random

/**
 * Created by zephyre on 1/14/16.
 */
class ViaeGateway @Inject() (@Named("etcd") configuration: Configuration) {

  lazy val handlerURL = {
    val config = configuration getConfig "services.viae" getOrElse Configuration.empty
    // 获得viae gateway服务器的地址
    val addresses = (config.subKeys map (subKey => {
      for {
        host <- config getString s"$subKey.host"
        port <- config getInt s"$subKey.port"
      } yield {
        host -> port
      }
    }) filter (_.nonEmpty) map (_.get)).toSeq

    // 随机取一个address
    val (host, port) = Random.shuffle(addresses).head

    val scheme = "http"
    s"$scheme://$host:$port/tasks"
  }

  /**
   * 向Viae Gateway发送任务
   * @return 任务ID
   */
  def sendTask(
    taskName: String,
    args: Option[Seq[Any]] = None,
    kwargs: Option[Map[String, Any]] = None,
    expire: Option[Int] = None,
    countdown: Option[Int] = None,
    routingKey: Option[String] = None
  ): Future[String] = {

    val mapper = new ObjectMapper()
    val factory = mapper.getNodeFactory

    val node = mapper.createObjectNode()
    node.put("task", taskName)
    args foreach (value => {
      val arrayNode = mapper.createArrayNode()
      value foreach (element => arrayNode.add(factory.pojoNode(element)))
      node.set("args", arrayNode)
    })
    kwargs foreach (value => {
      val dictNode = mapper.createObjectNode()
      value foreach {
        case (k, v) => dictNode.set(k, factory.pojoNode(v))
      }
      node.set("kwargs", dictNode)
    })
    expire foreach (node.put("expire", _))
    countdown foreach (node.put("countdown", _))
    routingKey foreach (node.put("routingKey", _))

    val body = mapper.writeValueAsString(node)

    import dispatch._, Defaults._

    val req = url(handlerURL).POST.setContentType("application/json", "UTF-8") << body
    val future = for {
      response <- Http(req.OK(as.String))
    } yield {
      Option(mapper.readTree(response) get "taskId") map (_.asText()) getOrElse {
        throw new RuntimeException(s"Unable to get taskId")
      }
    }

    import Implicits.TwitterConverter._

    future
  }
}
