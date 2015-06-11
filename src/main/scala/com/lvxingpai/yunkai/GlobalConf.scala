package com.lvxingpai.yunkai

import com.lvxingpai.appconfig.AppConfig
import com.lvxingpai.yunkai.Implicits._
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.language.postfixOps

/**
 * 配置模块
 */
object GlobalConf {
  lazy val conf = {
    val res = AppConfig.buildConfig(Some(Seq("yunkai"->"yunkai")), Some(Seq("mongo"->"mongo", "redis"->"redis")))
    Await.result(res, 10 seconds)
  }
}
