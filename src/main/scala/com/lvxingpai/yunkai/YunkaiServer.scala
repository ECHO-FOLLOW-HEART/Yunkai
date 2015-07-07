package com.lvxingpai.yunkai

import java.net.InetSocketAddress

import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol
import org.slf4j.LoggerFactory

/**
 * Created by zephyre on 5/4/15.
 */
object YunkaiServer extends App {
  main(args)

  override def main(args: Array[String]): Unit = {
    val conf = Global.conf.getConfig("yunkai")
    val name = conf.getString("name")
    val maxConcur = conf.getInt("maxConcurrentRequests")
    val port = conf.getInt("port")

    val logger = LoggerFactory.getLogger("root")
    val message = s"Server started. Listening on 0.0.0.0:$port"
    logger.info(message)

    val service = new Userservice$FinagleService(new UserServiceHandler, new TBinaryProtocol.Factory())

    ServerBuilder()
      .bindTo(new InetSocketAddress(port))
      .codec(ThriftServerFramedCodec())
      .name(name)
      .maxConcurrentRequests(maxConcur)
      .build(service)
  }

}
