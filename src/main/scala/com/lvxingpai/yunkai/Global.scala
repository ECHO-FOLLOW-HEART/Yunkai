package com.lvxingpai.yunkai

import com.lvxingpai.appconfig.{ AppConfig, EtcdConfBuilder, EtcdServiceBuilder }
import com.lvxingpai.yunkai.Implicits.defaultExecutionContext

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

/**
 * Created by zephyre on 6/15/15.
 */
object Global {

  val conf = {
    val defaultConf = AppConfig.defaultConfig

    // 运行模式: "production", "dev", "test"
    val runLevel = if (defaultConf.hasPath("runlevel")) {
      val rl = defaultConf.getString("runlevel")
      rl match {
        case "production" | "dev" | "test" =>
        case _ => throw new IllegalArgumentException(s"Invalid run level: $rl")
      }
      rl
    } else
      throw new IllegalArgumentException(s"The run level does not exist")

    // 根据runlevel获得配置项

    val backendKeysBase = Seq("redis-main" -> "redis", "rabbitmq", "smscenter")

    val backendKeys = backendKeysBase :+ (runLevel match {
      case "production" =>
        "mongo-production" -> "mongo"
      case "dev" =>
        "mongo-dev" -> "mongo"
    })

    val confKeys = (runLevel match {
      case "production" => "yunkai"
      case "dev" => "yunkai-dev" -> "yunkai"
    }) :: "yunkai-base" -> "yunkai" :: Nil

    // 配置节点
    val futureConfig = confKeys.foldLeft(EtcdConfBuilder())((builder, item) => {
      item match {
        case name: String => builder.addKey(name)
        case (name: String, alias: String) => builder.addKey(name, alias)
      }
    }).build()

    // 服务节点
    val futureService = backendKeys.foldLeft(EtcdServiceBuilder())((builder, item) => {
      item match {
        case name: String => builder.addKey(name)
        case (name: String, alias: String) => builder.addKey(name, alias)
      }
    }).build()

    // 将二者，再加上defaultConf，融合在一起
    val future = for {
      confList <- Future.sequence(futureService :: futureConfig :: Nil)
    } yield {
      (confList :+ defaultConf) reduce ((c1, c2) => c1 withFallback c2)
    }

    Await.result(future, 10 seconds)
  }
}
