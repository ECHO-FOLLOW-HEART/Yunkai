package com.aizou.yunkai

import com.redis.RedisClientPool
import com.twitter.util.{ Future, FuturePool }

/**
 * Created by zephyre on 5/19/15.
 */
object YunkaiUtils {
  var redisPool = {
    val conf = Global.conf.getConfig("backends")
    val host = conf.getString("redis.host")
    val port = conf.getInt("redis.port")
    new RedisClientPool(host, port)
  }

  def generateLong(key: String = "default")(implicit futurePool: FuturePool): Future[Option[Long]] = {
    futurePool {
      redisPool.withClient(_.incr(key))
    }
  }
}