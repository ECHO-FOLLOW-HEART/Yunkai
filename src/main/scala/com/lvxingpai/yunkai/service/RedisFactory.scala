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
    val backends = conf.getConfig("backends.redis")
    val servers = backends.root().toSeq map (item => {
      val (key, _) = item
      val host = backends.getString(s"$key.host")
      val port = backends.getInt(s"$key.port")
      host -> port
    })

    val db = conf.getInt("yunkai.redis.db")

    new RedisClientPool(servers.head._1, servers.head._2, database = db)
  }
}
