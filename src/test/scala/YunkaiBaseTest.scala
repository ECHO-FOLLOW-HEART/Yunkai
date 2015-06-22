import java.net.InetSocketAddress

import com.lvxingpai.yunkai.Gender
import com.lvxingpai.yunkai.Userservice.{ FinagledClient, FinagledService }
import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.twitter.finagle.builder.{ ClientBuilder, Server, ServerBuilder }
import com.twitter.finagle.thrift.{ ThriftClientFramedCodec, ThriftServerFramedCodec }
import org.apache.thrift.protocol.TBinaryProtocol.Factory
import org.specs2.Specification
import org.specs2.specification.BeforeAfterAll

import com.twitter.util.{ Future, Await, Duration }
import com.twitter.util.TimeConversions._

import scala.util.Random

/**
 * Created by zephyre on 6/19/15.
 */
abstract class YunkaiBaseTest extends Specification with BeforeAfterAll {
  sequential

  val port = 33689

  def invoke[T](future: Future[T], timeout: Duration = 100 seconds): T = Await.result(future, timeout)

  val userPreset = Map(Seq("Haizi", "CK", "Pengshuai", "Xiaoyao", "Xigua", "Luotuo") map (nickName => {
    val avatar = nickName + "avatar"
    val password = nickName + "password"
    val gender: Gender = if (Random.nextBoolean()) Gender.Male else Gender.Female
    val tel = "13800%s" format Random.nextInt(10000).toString
    val ret = (password, avatar, gender, tel)
    nickName -> scala.collection.mutable.Map(
      "avatar" -> avatar, "password" -> password, "tel" -> tel
    )
  }): _*)

  var server: Server = null
  var client: FinagledClient = null

  def createServer(): Server = {
    val service = new FinagledService(new UserServiceHandler, new Factory())

    val name = "yunkai-test"

    ServerBuilder()
      .bindTo(new InetSocketAddress(port))
      .codec(ThriftServerFramedCodec())
      .name(name)
      .maxConcurrentRequests(1000)
      .build(service)
  }

  def createClient(): FinagledClient = {
    val service = ClientBuilder()
      .hosts(new InetSocketAddress("127.0.0.1", port))
      .hostConnectionLimit(1000)
      .codec(ThriftClientFramedCodec())
      .build()
    new FinagledClient(service, new Factory())
  }

  override def afterAll(): Unit = {
    if (client != null) {
      client.service.close()
    }

    if (server != null) {
      server.close()
    }
  }
}