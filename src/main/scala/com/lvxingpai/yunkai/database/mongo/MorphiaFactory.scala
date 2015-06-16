package com.lvxingpai.yunkai.database.mongo

import com.lvxingpai.yunkai.Global
import com.lvxingpai.yunkai.model.{ ChatGroup, Credential, Relationship, UserInfo }
import com.mongodb.{ MongoClient, MongoClientOptions, MongoCredential, ServerAddress }
import org.mongodb.morphia.Morphia

import scala.collection.JavaConversions._

/**
 * Created by zephyre on 5/4/15.
 */
object MorphiaFactory {

  lazy val morphia = {
    val m = new Morphia()
    m.map(classOf[ChatGroup], classOf[Credential], classOf[Relationship], classOf[UserInfo])
    m
  }

  lazy val client = {
    val conf = Global.conf
    val mongoBackends = conf.getConfig("backends.mongo").entrySet().toSeq
    val serverAddresses = mongoBackends map (backend => {
      val tmp = backend.getValue.unwrapped().toString.split(":")
      val host = tmp(0)
      val port = tmp(1).toInt
      new ServerAddress(host, port)
    })
    val user = conf.getString("yunkai.mongo.user")
    val password = conf.getString("yunkai.mongo.password")
    val credential = MongoCredential.createScramSha1Credential(user, "admin", password.toCharArray)

    val options = new MongoClientOptions.Builder()
      //连接超时
      .connectTimeout(60000)
      //IO超时
      .socketTimeout(10000)
      //与数据库能够建立的最大连接数
      .connectionsPerHost(50)
      //每个连接可以有多少线程排队等待
      .threadsAllowedToBlockForConnectionMultiplier(50)
      .build()

    new MongoClient(serverAddresses, Seq(credential), options)
  }

  lazy val datastore = {
    val ds = morphia.createDatastore(client, "yunkai")
    ds.ensureIndexes()
    ds.ensureCaps()
    ds
  }
}
