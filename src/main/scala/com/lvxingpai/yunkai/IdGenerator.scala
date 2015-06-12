package com.lvxingpai.yunkai

import com.redis.RedisClientPool
import com.twitter.util.{ Future, FuturePool }

/**
 * 自增长ID生成器
 *
 */
object IdGenerator {
  lazy val redisPool = {
    val host = Global.conf.getString("redis.host")
    val port = Global.conf.getInt("redis.port")
    new RedisClientPool(host, port)
  }

  def generateId(key: String)(implicit futurePool: FuturePool): Future[Long] = futurePool {
    redisPool withClient {
      _.incr(key).get
    }
  }

  def getGeneratorBase(key: String)(implicit futurePool: FuturePool): Future[Option[Long]] = futurePool {
    redisPool withClient {
      _.get(key) map (_.toLong)
    }
  }

  def setGeneratorBase(key: String, value: Long)(implicit futurePool: FuturePool): Future[Unit] = futurePool {
    redisPool withClient {
      _.set(key, value)
    }
  }
}