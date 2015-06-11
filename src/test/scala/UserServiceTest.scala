import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.Gender
import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.lvxingpai.yunkai.model.UserInfo
import com.twitter.util.{ Await, FuturePool }
import org.mockito.Matchers
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.{ Query, UpdateOperations }
import org.specs2.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.core.SpecStructure

/**
 * 用户服务的测试代码
 *
 */
class UserServiceTest extends Specification with Mockito {
  val validUser = yunkai.UserInfo(9999L, "Foo", None, Some(Gender.Male), None, None)

  implicit val futurePool = FuturePool.unboundedPool

  implicit val userInfoDatastore = {
    val ds = mock[Datastore]
    val userInfoCls = classOf[UserInfo]

    val validQuery = mock[Query[UserInfo]]
    validQuery.get() returns UserInfo(validUser.userId, validUser.nickName)

    val invalidQuery = mock[Query[UserInfo]]
    invalidQuery.get() returns null

    val updateOps = mock[UpdateOperations[UserInfo]]

    updateOps.set(Matchers.anyString(), Matchers.any()) returns updateOps

    ds.find(===(userInfoCls), ===("userId"), argThat(===(validUser.userId))) returns validQuery
    ds.find(===(userInfoCls), ===("userId"), argThat(!==(validUser.userId))) returns invalidQuery

    ds.createUpdateOperations(===(userInfoCls)) returns updateOps

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
    Await.result(UserServiceHandler.getUserById(fakeUserId)) must_== null
  }

  def validUserInfoCheck = {
    val user = Await.result(UserServiceHandler.getUserById(validUser.userId))
    user.userId must_== validUser.userId
    user.nickName must_== validUser.nickName
  }

  //  def updateUserInfoCheck = {
  //    Await.result(UserServiceHandler.updateUserInfo(validUser.userId, Map(UserInfoProp.NickName -> "nickName")))
  //    there was one(userInfoDatastore).updateFirst(Matchers.any(classOf[Query[UserInfo]]),
  //      Matchers.any(classOf[UpdateOperations[UserInfo]]))
  //  }
}
