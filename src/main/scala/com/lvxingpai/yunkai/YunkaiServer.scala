package com.lvxingpai.yunkai

import java.net.InetSocketAddress

import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol

/**
 * Created by zephyre on 5/4/15.
 */
object YunkaiServer extends App {
  main(args)

  override def main(args: Array[String]): Unit = {
    val conf = Global.conf.getConfig("yunkai")
    val name = conf.getString("name")
    val maxConcur = conf.getInt("maxConcurrentRequests")

    val service = new Userservice$FinagleService(new UserServiceHandler, new TBinaryProtocol.Factory())

    ServerBuilder()
      .bindTo(new InetSocketAddress(conf.getInt("port")))
      .codec(ThriftServerFramedCodec())
      .name(name)
      .maxConcurrentRequests(maxConcur)
      .build(service)
  }

}
