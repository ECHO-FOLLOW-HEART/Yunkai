package com.example

import com.twitter.finagle.Thrift
import com.twitter.finagle.example.thriftscala.Hello
import com.twitter.util.{ Await, Future }

object ThriftServer {

  def main(args: Array[String]) {
    val server = Thrift.serveIface("localhost:8081", new Hello[Future] {
      def hi() = {
        Future("hi")
      }
    })
    Await.ready(server)
  }
}
