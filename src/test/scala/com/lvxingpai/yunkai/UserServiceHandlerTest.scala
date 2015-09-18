package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.{ IAccountManager, UserServiceHandler }
import com.twitter.util.{ Await, Duration, Future }
import org.scalatest.{ ShouldMatchers, GivenWhenThen, FeatureSpec }
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import com.lvxingpai.yunkai.Implicits.YunkaiConversions._
import scala.language.postfixOps
import com.twitter.util.TimeConversions._

/**
 * Created by pengyt on 2015/9/16.
 */
class UserServiceHandlerTest extends FeatureSpec with ShouldMatchers with GivenWhenThen with OneInstancePerTest with MockFactory {

  def waitFuture[T](future: Future[T], timeout: Duration = 60 seconds): T = Await.result(future, timeout)

  // 共享变量
  val userServiceHandler = new UserServiceHandler
  val mockManager = mock[IAccountManager]

  val fakeUserId: Long = 520520
  val user = com.lvxingpai.yunkai.model.UserInfo(fakeUserId, "luotuo")
  user.tel = "13811001100"

  val yunkaiUserInfo: com.lvxingpai.yunkai.UserInfo = user

  feature("the AccountManager can get a user's details") {
    scenario("an invalid user ID is provided") {
      Given("a illegal userId")
      val userId: Long = 100000
      userServiceHandler.accountManager = mockManager
      (mockManager.getUserById _).expects(userId, *, *).returning(Future(None))

      When("getUserById is invoked")
      intercept[NotFoundException] {
        waitFuture(userServiceHandler.getUserById(userId, Some(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Tel)), None))
      }
    }

    scenario("the user's nick name, ID and cell phone number are requested") {
      Given("a legal user info")
      userServiceHandler.accountManager = mockManager

      (mockManager.getUserById _).expects(fakeUserId, *, *).returning(Future(Some(yunkaiUserInfo)))

      When("getUserById is invoked")
      val ret = waitFuture(userServiceHandler.getUserById(fakeUserId, Some(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Tel)), None))

      Then("userId, nickName and tel will exist")
      ret.userId should be(yunkaiUserInfo.userId)
      ret.nickName should be(yunkaiUserInfo.nickName)
      ret.avatar should be(None)
      ret.tel should be(yunkaiUserInfo.tel)
      ret.signature should be(None)
      ret.gender should be(None)
    }

    //    scenario("multiple user's Ids are provided") {
    //      Given("a user ID list mixed with a fake user ID")
    //      val initialUserMap = Map(initialUsers map (entry => entry._1.userId -> entry._1): _*)
    //      val fakeUserId = (initialUsers map (_._1.userId) max) + 1
    //      val userIdList = (initialUsers map (_._1.userId)) :+ fakeUserId
    //
    //      When("retrive the users' details according to the user ID lsit")
    //      val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar, UserInfoProp.Tel)
    //      val userMap = waitFuture(new UserServiceHandler().getUsersById(userIdList, Some(properties), None))
    //
    //      Then("the user corresponding to the fake user ID should be null")
    //      userMap(fakeUserId) should be(null)
    //
    //      And("the details of other users should be correctly retrieved")
    //      initialUserMap foreach (entry => {
    //        val (userId, userInfo) = entry
    //        val actual = userMap(userId)
    //        actual.userId should be(userInfo.userId)
    //        actual.nickName should be(userInfo.nickName)
    //        actual.avatar should be(userInfo.avatar)
    //        actual.tel should be(userInfo.tel)
    //        actual.signature should be(None)
    //        actual.gender should be(None)
    //      })
    //    }
  }

  feature("the AccountManager can update a user's information") {
    scenario("the user does not exist") {
      Given("an invalid user ID is provided")
      val userId: Long = 100000
      userServiceHandler.accountManager = mockManager
      (mockManager.updateUserInfo _).expects(userId, *).returning(Future(null))

      When("updateUserInfo is invoked")
      intercept[NullPointerException] {
        waitFuture(userServiceHandler.updateUserInfo(userId, Map(UserInfoProp.NickName -> "foobar")))
      }
    }
    scenario("update a user's information") {
      Given("an valid user ID is provided")
      userServiceHandler.accountManager = mockManager
      (mockManager.updateUserInfo _).expects(fakeUserId, *).returning(Future(yunkaiUserInfo)).once

      When("updateUserInfo is invoked")
      waitFuture(userServiceHandler.updateUserInfo(fakeUserId, Map(UserInfoProp.NickName -> "foobar")))
    }
  }

  feature("the AccountManager can login a user") {
    scenario("login by user id") {
      Given("an valid cell phone number, password and source")
      userServiceHandler.accountManager = mockManager
      val password = "a1b2c3"
      (mockManager.login _).expects(user.tel, password, *).returning(Future(user)).once

      When("login is invoked")
      waitFuture(userServiceHandler.login(user.tel, password, "1"))
    }

    scenario("login by user oauth") {
      Given("an valid code and source")
      userServiceHandler.accountManager = mockManager
      val code: String = "11123dasdasd22131123dad"
      val source: String = "1"
      (mockManager.loginByWeixin _).expects(code, source).returning(Future(yunkaiUserInfo)).once

      When("loginByOAuth is invoked")
      waitFuture(userServiceHandler.loginByOAuth(code, source))
    }
  }

  feature("the AccountManager can create new users") {
    scenario("the user already exits") {
      Given("a user-signup form")
      val nickName: String = "luotuo"
      val tel: String = "13811001100"
      val password: String = "a1b2c3"
      val miscInfo: scala.collection.Map[UserInfoProp, String] = scala.collection.Map(
        UserInfoProp.Tel -> "13811001100"
      )
      userServiceHandler.accountManager = mockManager
      (mockManager.createUser _).expects(nickName, password, Some(tel)).returning(Future(null))

      When("createUser is invoked")
      intercept[NotFoundException] {
        waitFuture(userServiceHandler.createUser(nickName, password, Some(miscInfo)))
      }
    }

    scenario("a cell phone number is provided") {
      Given("a legal user info")
      val userId: Long = 100000
      val nickName: String = "xigua"
      val password: String = "a1b2c3"
      val userInfo = com.lvxingpai.yunkai.model.UserInfo(userId, nickName)
      userInfo.tel = "13888888888"
      val miscInfo: scala.collection.Map[UserInfoProp, String] = scala.collection.Map(
        UserInfoProp.Tel -> "13888888888"
      )
      userServiceHandler.accountManager = mockManager

      (mockManager.createUser _).expects(nickName, password, Some(userInfo.tel)).returning(Future(userInfo))

      When("createUser is invoked")
      val ret = waitFuture(userServiceHandler.createUser(nickName, password, Some(miscInfo)))

      Then("userId, nickName and tel will exist")
      ret.userId should be(userInfo.userId)
      ret.nickName should be(userInfo.nickName)
      ret.avatar should be(None)
      ret.tel.get should be(userInfo.tel)
      ret.signature should be(None)
      ret.gender should be(None)
    }
  }
}
