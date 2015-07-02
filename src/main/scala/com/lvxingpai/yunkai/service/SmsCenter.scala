package com.lvxingpai.yunkai.service

import java.net.InetSocketAddress

import com.lvxingpai.smscenter.SmsCenter.FinagledClient
import com.lvxingpai.yunkai.Global
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol.Factory

import scala.collection.JavaConversions._

/**
 * Created by zephyre on 7/2/15.
 */
object SmsCenter {
  lazy val client = {
    val conf = Global.conf
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
