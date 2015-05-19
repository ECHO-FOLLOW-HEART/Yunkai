import com.aizou.yunkai.Gender
import com.aizou.yunkai.handler.UserServiceHandler
import com.aizou.yunkai.model.UserInfo
import com.twitter.util.{ Await, FuturePool }
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.Query
import org.specs2.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.core.SpecStructure
import com.aizou.yunkai

/**
 * 用户服务的测试代码
 *
 */
class UserServiceTest extends Specification with Mockito {
  val validUser = yunkai.UserInfo(9999L, "Foo", None, Some(Gender.Male), None, None)

  implicit val futurePool = FuturePool.unboundedPool

  implicit val userInfoDatastore = {
    val ds = mock[Datastore]

    val validQuery = mock[Query[UserInfo]]
    validQuery.get() returns UserInfo(validUser.userId, validUser.nickName, validUser.avatar.orNull)

    val invalidQuery = mock[Query[UserInfo]]
    invalidQuery.get() returns null

    ds.find(===(classOf[UserInfo]), ===("userId"), argThat(===(validUser.userId))) returns validQuery
    ds.find(===(classOf[UserInfo]), ===("userId"), argThat(!==(validUser.userId))) returns invalidQuery

    ds
  }

  override def is: SpecStructure =
    s2"""
        |Specification for the user-info service:
        |
        |User-info service should:
        |Invalid userId should return None      $invalidUserInfoCheck
        |Valid userId should return user-info   $validUserInfoCheck
     """.stripMargin

  def invalidUserInfoCheck = {
    val fakeUserId = 10000L
    Await.result(UserServiceHandler.getUserById(fakeUserId)) must_== None
  }

  def validUserInfoCheck = {
    val user = Await.result(UserServiceHandler.getUserById(validUser.userId)).get
    user.userId must_== validUser.userId
    user.nickName must_== validUser.nickName
  }
}
