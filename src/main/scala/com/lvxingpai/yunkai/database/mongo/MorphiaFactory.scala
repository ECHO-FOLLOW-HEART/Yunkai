package com.lvxingpai.yunkai.database.mongo

import java.util.UUID

import com.lvxingpai.yunkai.Global
import com.lvxingpai.yunkai.model._
import com.mongodb._
import org.mongodb.morphia.Morphia
import org.mongodb.morphia.annotations.Property

import scala.collection.JavaConversions._
import scala.language.postfixOps

/**
 * Created by zephyre on 5/4/15.
 */
object MorphiaFactory {

  lazy val morphia = {
    val m = new Morphia()
    m.map(classOf[ChatGroup], classOf[ContactRequest], classOf[Credential], classOf[Relationship], classOf[UserInfo])
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
    val dbName = conf.getString("yunkai.mongo.db")
    val credential = MongoCredential.createScramSha1Credential(user, dbName, password.toCharArray)

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
    val dbName = Global.conf.getString("yunkai.mongo.db")
    val ds = morphia.createDatastore(client, dbName)

    // 对于没有使用手机号码注册的用户，使用UUID生成placeholder
    ds.createQuery(classOf[UserInfo]).field(UserInfo.fdTel).equal(null).retrievedFields(true,
      UserInfo.fdUserId).asList().toSeq foreach (entry => {
      val query = ds.createQuery(classOf[UserInfo]).field(UserInfo.fdUserId).equal(entry.userId)
      val ops = ds.createUpdateOperations(classOf[UserInfo]).set(UserInfo.fdTel, UUID.randomUUID().toString)
      ds.update(query, ops)
    })
    ds.ensureIndexes()
    ds.ensureCaps()
    ds
  }

  def getCollection[T](cls: Class[T]): DBCollection = {
    val annotation = cls.getAnnotation(classOf[Property])
    val colName = if (annotation != null)
      annotation.value()
    else
      cls.getSimpleName
    val db = datastore.getDB
    db.getCollection(colName)
  }
}
