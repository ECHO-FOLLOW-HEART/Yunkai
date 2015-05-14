import com.aizou.yunkai.UserInfoProp
import com.twitter.util.Await

/**
 * Created by zephyre on 5/4/15.
 */
class UserInfoHandlerTest extends TestCase {
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
        val userId = 100018
        val user = Await.result(client.getUserById(userId))
        user.userId must beEqualTo(userId)
      } finally {
        client.service.close()
        server.close()
      }
    }

    "Change user info" >> {
      val (server, port) = createServer()
      val client = createClient(port)
      try {
        val userId = 100018
        val nickName = "name%d" format System.currentTimeMillis
        Await.result(client.updateUserInfo(userId, Map(UserInfoProp.NickName -> nickName)))
        val user = Await.result(client.getUserById(userId))
        user.nickName must_== nickName
      } finally {
        client.service.close()
        server.close()
      }
    }

    "Add/remove another user as contact" >> {
      val (server, port) = createServer()
      val client = createClient(port)
      try {
        val self = 100018
        val target = 100019

        // Add
        Await.result(client.addContact(self, target))
        Await.result(client.isContact(self, target)) must_== true
        Await.result(client.getContactList(self, None, None, None)) map (_.userId) contains target must_== true
        Await.result(client.getContactList(target, None, None, None)) map (_.userId) contains self must_== true

        // Remove
        Await.result(client.removeContact(self, Seq(target)))
        Await.result(client.isContact(self, target)) must_== false
        Await.result(client.getContactList(self, None, None, None)) map (_.userId) contains target must_== false
        Await.result(client.getContactList(target, None, None, None)) map (_.userId) contains self must_== false
      } finally {
        client.service.close()
        server.close()
      }
    }
  }
}
