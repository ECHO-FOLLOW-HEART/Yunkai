import java.net.InetSocketAddress

import com.lvxingpai.yunkai.Gender
import com.lvxingpai.yunkai.Userservice.{ FinagledClient, FinagledService }
import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.lvxingpai.yunkai.model._
import com.twitter.finagle.builder.{ ClientBuilder, Server, ServerBuilder }
import com.twitter.finagle.thrift.{ ThriftClientFramedCodec, ThriftServerFramedCodec }
import com.twitter.util.TimeConversions._
import com.twitter.util.{ Await, Duration, Future }
import org.apache.thrift.protocol.TBinaryProtocol.Factory
import org.specs2.Specification
import org.specs2.specification.BeforeAfterAll
import org.specs2.specification.core.SpecStructure

import scala.util.Random

/**
 * Created by zephyre on 6/18/15.
 */
class AccountManagerTest extends Specification with BeforeAfterAll {

  sequential

  val port = 33689
  val userPreset = Map(Seq("Haizi", "CK", "Pengshuai", "Xiaoyao", "Xigua", "Luotuo") map (nickName => {
    val avatar = nickName + "avatar"
    val password = nickName + "password"
    val gender: Gender = if (Random.nextBoolean()) Gender.Male else Gender.Female
    val tel = "13800%s" format Random.nextInt(10000).toString
    val ret = (password, avatar, gender, tel)
    nickName -> ret
  }): _*)
  var server: Server = null
  var client: FinagledClient = null

  override def beforeAll(): Unit = {
    cleanDatabase()
    server = createServer()
    client = createClient()
    createUser
  }

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

  def cleanDatabase(): Unit = {
    val ds = MorphiaFactory.datastore
    Seq(classOf[UserInfo], classOf[ChatGroup], classOf[Conversation], classOf[Credential],
      classOf[Relationship], classOf[Sequence]) foreach (cls => {
        ds.delete(ds.createQuery(cls))
      })
  }

  override def is: SpecStructure =
    s2"""
        |This is a specification to check AccountManager
        |
        |The AccountManager should:
        |can login                     $login
      """.stripMargin

  def createUser = {
    val userInfoList = userPreset.toSeq map (entry => {
      val name = entry._1
      val data = entry._2
      val password = data._1
      val tel = data._4

      name -> invoke(client.createUser(name, password, Some(tel)))
    })

    userInfoList map (entry => {
      val (nickName, userInfo) = entry
      userInfo.userId.toInt must beGreaterThan(1)
      userInfo.nickName must_=== nickName
    })
  }

  def invoke[T](future: Future[T], timeout: Duration = 100 seconds): T = Await.result(future, timeout)

  def login = {
    val userInfoList = userPreset.toSeq map (entry => {
      val name = entry._1
      val password = entry._2._1
      val tel = entry._2._4
      name -> invoke(client.login(tel, password))
    })

    userInfoList map (entry => {
      val (nickName, user) = entry

      user.userId.toInt must beGreaterThan(1)
      user.nickName must_=== nickName
    })
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
