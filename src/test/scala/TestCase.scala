import java.net.InetSocketAddress

import com.aizou.yunkai.Userservice
import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.twitter.finagle.builder.{ ClientBuilder, ServerBuilder }
import com.twitter.finagle.thrift.{ ThriftClientFramedCodec, ThriftServerFramedCodec }
import com.twitter.util.Closable
import org.apache.thrift.protocol.TBinaryProtocol

/**
 * Created by zephyre on 5/5/15.
 */
abstract class TestCase extends org.specs2.mutable.Specification {
  def createServer(): (Closable, Int) = {
    val server = ServerBuilder()
      .bindTo(new InetSocketAddress(0))
      .codec(ThriftServerFramedCodec())
      .name("server")
      .build(new Userservice.FinagledService(new UserServiceHandler, new TBinaryProtocol.Factory()))
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
}
