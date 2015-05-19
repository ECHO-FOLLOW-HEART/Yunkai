package com.aizou.yunkai.database

import com.aizou.yunkai.database.mongo.MorphiaFactory

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit val mongoDatastore = MorphiaFactory.getDatastore()
}
