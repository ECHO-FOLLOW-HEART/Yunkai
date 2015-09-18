package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.{ IAccountManager, UserServiceHandler }
import com.twitter.util.{ Await, Duration, Future }
import org.scalatest.{ ShouldMatchers, GivenWhenThen, FeatureSpec }
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
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

  feature("the AccountManager can get a user's details") {

    scenario("an invalid user ID is provided") {
      Given("Given a illegal userId")
      val userId: Long = 100000
      userServiceHandler.manager = mockManager
      (mockManager.getUserById _).expects(userId, *, *).returning(Future(None))

      When("input a illegal userId, expects a NotFoundException")
      intercept[NotFoundException] {
        waitFuture(userServiceHandler.getUserById(userId, Some(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Tel)), None))
      }
    }

    scenario("the user's nick name, ID and cell phone number are requested") {
      Given("Given a legal user info")
      userServiceHandler.manager = mockManager
      val userId: Long = 100001
      val userInfo = com.lvxingpai.yunkai.model.UserInfo(userId, "luotuo")
      userInfo.tel = "13811001100"
      import com.lvxingpai.yunkai.Implicits.YunkaiConversions._
      val yunkaiUserInfo: com.lvxingpai.yunkai.UserInfo = userInfo
      (mockManager.getUserById _).expects(userId, *, *).returning(Future(Some(yunkaiUserInfo)))

      When("input a lagal userId")
      val ret = waitFuture(userServiceHandler.getUserById(userId, Some(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Tel)), None))

      Then("userId, nickName and tel will exist")
      ret.userId should be(userInfo.userId)
      ret.nickName should be(userInfo.nickName)
      ret.avatar should be(None)
      ret.tel.get should be(userInfo.tel)
      ret.signature should be(None)
      ret.gender should be(None)
    }

    //        scenario("multiple user's Ids are provided") {
    //          Given("a user ID list mixed with a fake user ID")
    //          val initialUserMap = Map(initialUsers map (entry => entry._1.userId -> entry._1): _*)
    //          val fakeUserId = (initialUsers map (_._1.userId) max) + 1
    //          val userIdList = (initialUsers map (_._1.userId)) :+ fakeUserId
    //
    //          When("retrive the users' details according to the user ID lsit")
    //          val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar, UserInfoProp.Tel)
    //          val userMap = waitFuture(new UserServiceHandler().getUsersById(userIdList, Some(properties), None))
    //
    //          Then("the user corresponding to the fake user ID should be null")
    //          userMap(fakeUserId) should be(null)
    //
    //          And("the details of other users should be correctly retrieved")
    //          initialUserMap foreach (entry => {
    //            val (userId, userInfo) = entry
    //            val actual = userMap(userId)
    //            actual.userId should be(userInfo.userId)
    //            actual.nickName should be(userInfo.nickName)
    //            actual.avatar should be(userInfo.avatar)
    //            actual.tel should be(userInfo.tel)
    //            actual.signature should be(None)
    //            actual.gender should be(None)
    //          })
    //        }
  }

}
