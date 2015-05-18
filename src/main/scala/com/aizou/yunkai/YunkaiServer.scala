package com.aizou.yunkai

import java.net.InetSocketAddress

import com.aizou.yunkai.handler.UserServiceHandler
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol

/**
 * Created by zephyre on 5/4/15.
 */
object YunkaiServer extends App {
  main(args)

  override def main(args: Array[String]): Unit = {
    val conf = AppConfig.conf
    val namePath = "service.user.name"
    val name = if (conf.hasPath(namePath)) conf.getString(namePath) else "unnamed"
    val maxConcurPath = "service.user.maxConcurrentRequests"
    val maxConcur = if (conf.hasPath(maxConcurPath)) conf.getInt(maxConcurPath) else 1000

    val service = new Userservice$FinagleService(new UserServiceHandler, new TBinaryProtocol.Factory())

    ServerBuilder()
      .bindTo(new InetSocketAddress(conf.getInt("service.user.port")))
      .codec(ThriftServerFramedCodec())
      .name(name)
      .maxConcurrentRequests(maxConcur)
      .build(service)
  }

}
