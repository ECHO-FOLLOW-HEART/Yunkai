package com.aizou.yunkai

import com.aizou.yunkai.database.mongo.MorphiaFactory
import com.twitter.util.FuturePool

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit lazy val mongoDatastore = MorphiaFactory.getDatastore()

  implicit lazy val defaultFuturePool = FuturePool.unboundedPool

  implicit lazy val defaultExecutionContext = scala.concurrent.ExecutionContext
    .fromExecutorService(defaultFuturePool.executor)
}
