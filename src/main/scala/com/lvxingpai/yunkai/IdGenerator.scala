package com.lvxingpai.yunkai

import com.redis.RedisClientPool
import com.twitter.util.{ Future, FuturePool }

import scala.collection.JavaConversions._

/**
 * 自增长ID生成器
 *
 */
object IdGenerator {
  lazy val redisPool = {

    val conf = Global.conf
    val redisEndpoints = conf.getConfig("backends.redis").entrySet().toSeq map (backend => {
      val tmp = backend.getValue.unwrapped().toString.split(":")
      val host = tmp(0)
      val port = tmp(1).toInt
      host -> port
    })

    val host = redisEndpoints.head._1
    val port = redisEndpoints.head._2
    val redisDB = Global.conf.getInt("yunkai.redis.db")

    new RedisClientPool(host, port, database = redisDB)
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