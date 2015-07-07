package com.lvxingpai.yunkai.service

import java.net.InetSocketAddress

import com.lvxingpai.smscenter.SmsCenter.FinagledClient
import com.lvxingpai.yunkai.Global
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import com.twitter.util.Future
import com.typesafe.config.ConfigException
import org.apache.thrift.protocol.TBinaryProtocol.Factory

import scala.collection.JavaConversions._

/**
 * Created by zephyre on 7/2/15.
 */
object SmsCenter {
  lazy val client = {
    val conf = Global.conf

    // 在两种情况下不发送短信：
    // * runlevel = test
    // * 系统属性中，smscenter.disabled = true
    val disableSms = {
      // 在test模式下，或者smscenter=false，不发送短信
      val confDisabled = try {
        conf.getBoolean("smscenter.disabled")
      } catch {
        case _: ConfigException.Missing => false
      }
      conf.getString("runlevel") == "test" || confDisabled
    }

    if (disableSms) {
      object client extends FinagledClient(null, null) {
        override def sendSms(message: String, recipients: Seq[String] = Seq[String]()): Future[String] = {
          Future[String] {
            null
          }
        }
      }
      client.asInstanceOf[FinagledClient]
    }
    else {
      val backends = conf.getConfig("backends.smscenter")
      val servers = backends.root().toSeq map (item => {
        val (key, _) = item
        val host = backends.getString(s"$key.host")
        val port = backends.getInt(s"$key.port")
        host -> port
      })

      val service = ClientBuilder().hosts(new InetSocketAddress(servers.head._1, servers.head._2))
        .hostConnectionLimit(1000)
        .codec(ThriftClientFramedCodec())
        .build()

      new FinagledClient(service, new Factory())
    }
  }
}
