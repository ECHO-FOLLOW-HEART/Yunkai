package com.lvxingpai.yunkai

import com.lvxingpai.appconfig.{ AppConfig, EtcdBuilder, EtcdConfBuilder, EtcdServiceBuilder }
import com.lvxingpai.yunkai.Implicits.defaultExecutionContext
import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
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

    val backendKeys = defaultConf.getConfig(s"backendKeys.$runLevel")
    val confKeys = defaultConf.getConfig(s"confKeys.$runLevel")

    def func(conf: Config, builder: EtcdBuilder): Future[Config] = {
      val entries = conf.entrySet().toSeq map (item => item.getKey -> item.getValue.unwrapped().toString)
      entries.foldLeft(builder)((b, pair) => {
        b.addKey(pair._1, pair._2)
      }).build()
    }

    val future = for {
      confList <- Future.sequence(Seq(func(backendKeys, EtcdServiceBuilder()), func(confKeys, EtcdConfBuilder())))
    } yield {
      (confList :+ defaultConf).reduce((c1, c2) => {
        c1.withFallback(c2)
      })
    }

    Await.result(future, 10 seconds)
  }
}
