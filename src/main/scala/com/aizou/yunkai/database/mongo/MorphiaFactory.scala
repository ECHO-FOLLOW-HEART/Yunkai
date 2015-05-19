package com.aizou.yunkai.database.mongo

import com.aizou.yunkai.AppConfig
import com.aizou.yunkai.model.{ Relationship, UserInfo }
import com.mongodb.{ MongoClient, ServerAddress }
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

    val host = AppConfig.conf.getString("mongo.host")
    val port = AppConfig.conf.getInt("mongo.port")

    val serverList = Seq(buildServerAddr(s"$host:$port").get)
    new MongoClient(serverList)
  }

  private val morphia = {
    val morphia = new Morphia
    new ValidationExtension(morphia)
    morphia.map(classOf[UserInfo])
    morphia.map(classOf[Relationship])
    morphia
  }

  def getDatastore(name: String = "test"): Datastore = {
    val ds = morphia.createDatastore(client, name)
    ds.ensureIndexes(true)
    ds
  }
}