package com.lvxingpai.yunkai

import java.net.InetSocketAddress

import com.google.inject.Key
import com.google.inject.name.Names
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol

/**
 * Created by zephyre on 5/4/15.
 */
object YunkaiServer extends App {
  val injector = Global.injector
  val handler = injector.getInstance(classOf[UserServiceHandler])
  val conf = injector.getInstance(Key.get(classOf[Configuration], Names.named("etcd")))

  val port = conf getInt "yunkai.port" getOrElse 9000
  val maxConcur = conf getInt "yunkai.maxConcurrentRequests" getOrElse 1000
  val service = new Userservice$FinagleService(handler, new TBinaryProtocol.Factory())
  ServerBuilder()
    .bindTo(new InetSocketAddress(port))
    .codec(ThriftServerFramedCodec())
    .name("yunkai")
    .maxConcurrentRequests(maxConcur)
    .build(service)
}
