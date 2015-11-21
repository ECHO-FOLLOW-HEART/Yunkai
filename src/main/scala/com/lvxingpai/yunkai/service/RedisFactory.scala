package com.lvxingpai.yunkai.service

import com.lvxingpai.yunkai.Global
import com.redis.RedisClientPool

import scala.collection.JavaConversions._

/**
 * Created by zephyre on 7/2/15.
 */
object RedisFactory {
  lazy val pool = {
    val conf = Global.conf
    val services = conf.getConfig("services.redis")
    val servers = services.root().toSeq map (item => {
      val (key, _) = item
      val host = services.getString(s"$key.host")
      val port = services.getInt(s"$key.port")
      host -> port
    })

    val db = conf.getInt("yunkai.redis.db")

    new RedisClientPool(servers.head._1, servers.head._2, database = db)
  }
}
