package com.aizou.yunkai

import com.lvxingpai.appconfig.AppConfig

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import com.aizou.yunkai.Implicits._

/**
 * Created by zephyre on 6/15/15.
 */
object Global {

  val conf = {
    val defaultConf = AppConfig.defaultConfig
    // 是否为生产环境
    val isProduction = defaultConf.hasPath("runlevel") && defaultConf.getString("runlevel") == "production"
    val mongoKey = if (isProduction) "mongo" else "mongo-dev"
    val timeout = 30 seconds

    val confFuture = AppConfig.buildConfig(
      Some(Seq("yunkai" -> "yunkai")),
      Some(Seq(mongoKey -> "mongo", "redis-main" -> "redis", "rabbitmq" -> "rabbitmq")))

    Await.result(confFuture, timeout)
  }
}
