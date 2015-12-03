package com.lvxingpai.yunkai.inject

import java.net.InetSocketAddress

import com.google.inject.Provider
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.smscenter.SmsCenter
import com.lvxingpai.smscenter.SmsCenter.FinagledClient
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol.Factory

/**
 * Created by zephyre on 11/21/15.
 */
class SmsCenterProvider(config: Configuration) extends Provider[SmsCenter.FinagledClient] {
  lazy val get: FinagledClient = {
    val ret = for {
      services <- config getConfig "smscenter"
    } yield {
      val entries = (services.subKeys map (key => {
        for {
          host <- services getString s"$key.host"
          port <- services getInt s"$key.port"
        } yield {
          host -> port
        }
      })).toSeq filter (_.nonEmpty) map (_.get)

      val servers = entries map (entry => new InetSocketAddress(entry._1, entry._2))

      val service = ClientBuilder().hosts(servers)
        .hostConnectionLimit(1000)
        .codec(ThriftClientFramedCodec())
        .build()

      new SmsCenter.FinagledClient(service, new Factory())
    }
    ret.get
  }
}
