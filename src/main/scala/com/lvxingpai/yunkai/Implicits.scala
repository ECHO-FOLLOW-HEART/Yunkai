package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.twitter.util.FuturePool

import scala.concurrent.ExecutionContext

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit lazy val mongoDatastore = MorphiaFactory.getDatastore()

  implicit lazy val defaultFuturePool = FuturePool.unboundedPool

  implicit lazy val defaultExecutionContext = ExecutionContext.fromExecutorService(defaultFuturePool.executor)
}
