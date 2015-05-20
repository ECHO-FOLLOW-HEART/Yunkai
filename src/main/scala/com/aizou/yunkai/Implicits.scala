package com.aizou.yunkai

import com.aizou.yunkai.database.mongo.MorphiaFactory
import com.twitter.util.FuturePool

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit val mongoDatastore = MorphiaFactory.getDatastore()

  implicit val defaultExecutionContext = FuturePool.unboundedPool
}
