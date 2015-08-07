package com.lvxingpai.yunkai.service

import java.util.UUID

import com.lvxingpai.yunkai.Global
import com.lvxingpai.yunkai.model._
import com.mongodb._
import com.typesafe.config.ConfigException
import org.mongodb.morphia.Morphia
import org.mongodb.morphia.annotations.Property
import org.slf4j.LoggerFactory

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
    val mongoBackends = conf.getConfig("backends.mongo")
    val serverAddresses = mongoBackends.root().toSeq map (item => {
      val (key, _) = item
      val host = mongoBackends.getString(s"$key.host")
      val port = mongoBackends.getInt(s"$key.port")
      new ServerAddress(host, port)
    })

    val dbName = conf.getString("yunkai.mongo.db")
    val userOpt = Option(try {
      conf.getString("yunkai.mongo.user")
    } catch {
      case _: ConfigException.Missing => null
    })
    val passwordOpt = Option(try {
      conf.getString("yunkai.mongo.password")
    } catch {
      case _: ConfigException.Missing => null
    })
    val credentialOpt = for {
      user <- userOpt
      password <- passwordOpt
    } yield {
      MongoCredential.createScramSha1Credential(user, dbName, password.toCharArray)
    }

    LoggerFactory.getLogger(MorphiaFactory.getClass).info(s"Connected to MongoDB: dbName=$dbName")

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

    if (credentialOpt nonEmpty)
      new MongoClient(serverAddresses, Seq(credentialOpt.get), options)
    else
      new MongoClient(serverAddresses, options)
  }

  lazy val datastore = {
    val dbName = Global.conf.getString("yunkai.mongo.db")
    val ds = morphia.createDatastore(client, dbName)

    // 消除fdTel为null的项目
    val cls = classOf[UserInfo]
    val query = ds.createQuery(cls) field UserInfo.fdTel equal null
    query.asList().toSeq foreach (u => {
      val query = ds.createQuery(cls) field UserInfo.fdUserId equal u.userId
      val ops = ds.createUpdateOperations(cls).set(UserInfo.fdTel, UUID.randomUUID().toString)
      ds.updateFirst(query, ops)
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
