package com.lvxingpai.yunkai.inject

import java.net.InetSocketAddress

import com.google.inject.Provider
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.idgen.IdGen
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol.Factory

/**
 * Created by zephyre on 11/21/15.
 */
class IdGenProvider(config: Configuration) extends Provider[IdGen.FinagledClient] {
  lazy val get: IdGen.FinagledClient = {
    val ret = for {
      services <- config getConfig "idgen"
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

      new IdGen.FinagledClient(service, new Factory())
    }
    ret.get
  }
}
