package com.lvxingpai.yunkai.model

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.Id

import scala.beans.BeanProperty

/**
 * Created by zephyre on 7/12/15.
 */
abstract class AbstractEntity {
  @BeanProperty
  @Id
  var id: ObjectId = new ObjectId()
}
