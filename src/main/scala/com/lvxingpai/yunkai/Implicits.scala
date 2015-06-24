package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.twitter.util.FuturePool

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit lazy val mongoDatastore = MorphiaFactory.datastore

  implicit lazy val defaultFuturePool = FuturePool.unboundedPool

  implicit lazy val defaultExecutionContext = scala.concurrent.ExecutionContext
    .fromExecutorService(defaultFuturePool.executor)
}
