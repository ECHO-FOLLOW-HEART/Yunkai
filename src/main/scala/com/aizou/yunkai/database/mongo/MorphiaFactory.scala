package com.aizou.yunkai.database.mongo

import com.aizou.yunkai.model.UserInfo
import com.mongodb.{ MongoClient, ServerAddress }
import com.typesafe.config.ConfigFactory
import org.mongodb.morphia.{ Datastore, Morphia, ValidationExtension }

import scala.collection.JavaConversions._

/**
 * Created by zephyre on 5/4/15.
 */
object MorphiaFactory {
  private val client = {
    def buildServerAddr(address: String): Option[ServerAddress] = {
      address.split(":") match {
        case Array(host, portStr) => try {
          new Some(new ServerAddress(host, portStr.toInt))
        } catch {
          case _: Throwable => None
        }
        case _ => None
      }
    }

    val serverList = ConfigFactory.load().getList("mongo.servers")
      .map(v => buildServerAddr(v.unwrapped.toString)).toSeq.flatten
    new MongoClient(serverList)
  }

  private val morphia = {
    val morphia = new Morphia
    new ValidationExtension(morphia)
    morphia.map(classOf[UserInfo])
    morphia
  }

  def getDatastore(name: String = "test"): Datastore = {
    val ds = morphia.createDatastore(client, name)
    ds.ensureIndexes(true)
    ds
  }
}