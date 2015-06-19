package com.lvxingpai.yunkai

import com.lvxingpai.appconfig.AppConfig
import com.lvxingpai.yunkai.Implicits.defaultExecutionContext

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by zephyre on 6/15/15.
 */
object Global {

  val conf = {
    val defaultConf = AppConfig.defaultConfig

    // 运行模式: "production", "dev", "test"
    val runLevel = if (defaultConf.hasPath("runLevel")) {
      val rl = defaultConf.getString("runLevel")
      rl match {
        case "production" | "dev" | "test" =>
        case _ => throw new IllegalArgumentException(s"Invalid run level: $rl")
      }
      rl
    } else
      "test"

    val backendKeys = defaultConf.getConfig(s"backendKeys.$runLevel")
    val confKeys = defaultConf.getConfig(s"confKeys.$runLevel")

    val backendEntries = backendKeys.entrySet().toSeq map (v => v.getKey -> v.getValue.unwrapped().toString)
    val confEntries = confKeys.entrySet().toSeq map (v => v.getKey -> v.getValue.unwrapped().toString)
    val timeout = 30 seconds

    val confFuture = AppConfig.buildConfig(Some(confEntries), Some(backendEntries))

    Await.result(confFuture, timeout)
  }
}
