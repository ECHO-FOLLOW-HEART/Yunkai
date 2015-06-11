package com.lvxingpai.yunkai

import com.redis.RedisClientPool
import com.twitter.util.{ Future, FuturePool }

/**
 * Created by zephyre on 5/19/15.
 */
object YunkaiUtils {
  var redisPool = {
    val host = AppConfig.conf.getString("redis.host")
    val port = AppConfig.conf.getInt("redis.port")
    new RedisClientPool(host, port)
  }

  def generateLong(key: String = "default")(implicit futurePool: FuturePool): Future[Option[Long]] = {
    futurePool {
      redisPool.withClient(_.incr(key))
    }
  }
}