import java.net.InetSocketAddress

import com.aizou.yunkai.Userservice
import com.aizou.yunkai.handler.UserInfoHandler
import com.twitter.finagle.builder.{ ClientBuilder, ServerBuilder }
import com.twitter.finagle.thrift.{ ThriftClientFramedCodec, ThriftServerFramedCodec }
import com.twitter.util.{ Await, Closable }
import org.apache.thrift.protocol.TBinaryProtocol

/**
 * Created by zephyre on 5/4/15.
 */
class UserInfoHandlerTest extends org.specs2.mutable.Specification {
  def createServer(): (Closable, Int) = {
    val server = ServerBuilder()
      .bindTo(new InetSocketAddress(0))
      .codec(ThriftServerFramedCodec())
      .name("server")
      .build(new Userservice.FinagledService(new UserInfoHandler, new TBinaryProtocol.Factory()))
    val port = server.localAddress.asInstanceOf[InetSocketAddress].getPort
    (server, port)
  }

  def createClient(port: Int) = {
    val clientService = ClientBuilder()
      .hosts(new InetSocketAddress("localhost", port))
      .hostConnectionLimit(100)
      .name("client")
      .codec(ThriftClientFramedCodec())
      .build()

    new Userservice.FinagledClient(clientService, new TBinaryProtocol.Factory())
  }

  "The specification for the user-info service" >> {

    "Returns null if a user cannot be found" >> {
      val (server, port) = createServer()
      val client = createClient(port)
      try {
        val badUserId = 1
        val user = try {
          Await.result(client.getUserById(badUserId))
        } catch {
          case ex: Throwable => null
        }
        user must beNull
      } finally {
        client.service.close()
        server.close()
      }
    }

    "Looks up users by userId" >> {
      val (server, port) = createServer()
      val client = createClient(port)
      try {
        val userId = 100076
        val user = Await.result(client.getUserById(userId))
        user.userId must_== userId
      } finally {
        client.service.close()
        server.close()
      }
    }
  }
}
