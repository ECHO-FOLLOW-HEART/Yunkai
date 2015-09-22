package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.{ GroupManager, AccountManager, UserServiceHandler }
import com.twitter.util.{ Await, Duration, Future }
import org.bson.types.ObjectId
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
  val mockAccountManager = mock[AccountManager]

  val fakeUserId: Long = 520520
  val user = com.lvxingpai.yunkai.model.UserInfo(fakeUserId, "luotuo")
  user.tel = "13811001100"

  val haiziId: Long = 100033
  val haizi = com.lvxingpai.yunkai.model.UserInfo(haiziId, "haizi")
  haizi.tel = "15300167111"

  val mockGroupManager = mock[GroupManager]
  val xiaoyaoId: Long = 100053
  val xiaoyao = com.lvxingpai.yunkai.model.UserInfo(xiaoyaoId, "xiaoyao")
  xiaoyao.tel = "15300167102"

  val chatGroupId: Long = 200000
  val members: Seq[Long] = Seq(100053, 520520)
  val chatGroup = com.lvxingpai.yunkai.model.ChatGroup(fakeUserId, chatGroupId, members)
  chatGroup.name = "test group"
  chatGroup.groupDesc = "this is a test chat group"

  val userServiceHandler = new UserServiceHandler(mockAccountManager, mockGroupManager)

  val yunkaiUserInfo: com.lvxingpai.yunkai.UserInfo = user

  feature("the AccountManager can get a user's details") {
    scenario("an invalid user ID is provided") {
      Given("a illegal userId")
      val userId: Long = 100000
      (mockAccountManager.getUserById _).expects(userId, *, *).returning(Future(None))

      When("getUserById is invoked")
      intercept[NotFoundException] {
        waitFuture(userServiceHandler.getUserById(userId, Some(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Tel)), None))
      }
    }

    scenario("the user's nick name, ID and cell phone number are requested") {
      Given("a legal user info")

      (mockAccountManager.getUserById _).expects(fakeUserId, *, *).returning(Future(Some(yunkaiUserInfo)))

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

    scenario("multiple user's Ids are provided") {
      Given("a user ID list mixed with a fake user ID")
      val invalidUserId: Long = 100000
      val userIdList = Seq(invalidUserId) :+ fakeUserId
      val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Tel)
      (mockAccountManager.getUsersByIdList _).expects(properties, None, userIdList).returning(Future(
        Map(
          invalidUserId -> None,
          fakeUserId -> Some(yunkaiUserInfo)
        )))

      When("retrive the users' details according to the user id list")
      val userMap = waitFuture(userServiceHandler.getUsersById(userIdList, Some(properties), None))

      Then("the user corresponding to the fake user ID should be null")
      userMap(invalidUserId) should be(null)

      And("the details of other users should be correctly retrieved")
      val actual = userMap(fakeUserId)
      actual.userId should be(user.userId)
      actual.nickName should be(user.nickName)
      actual.avatar should be(None)
      actual.tel.get should be(user.tel)
      actual.signature should be(None)
      actual.gender should be(None)
    }
  }

  feature("the AccountManager can update a user's information") {
    scenario("the user does not exist") {
      Given("an invalid user ID is provided")
      val userId: Long = 100000
      (mockAccountManager.updateUserInfo _).expects(userId, *).returning(Future(null))

      When("updateUserInfo is invoked")
      intercept[NullPointerException] {
        waitFuture(userServiceHandler.updateUserInfo(userId, Map(UserInfoProp.NickName -> "foobar")))
      }
    }
    scenario("update a user's information") {
      Given("an valid user ID is provided")
      (mockAccountManager.updateUserInfo _).expects(fakeUserId, *).returning(Future(yunkaiUserInfo)).once

      When("updateUserInfo is invoked")
      waitFuture(userServiceHandler.updateUserInfo(fakeUserId, Map(UserInfoProp.NickName -> "foobar")))
    }
  }

  feature("the AccountManager can login a user") {
    scenario("login by user id") {
      Given("an valid cell phone number, password and source")
      val password = "a1b2c3"
      (mockAccountManager.login _).expects(user.tel, password, *).returning(Future(user)).once

      When("login is invoked")
      waitFuture(userServiceHandler.login(user.tel, password, "1"))
    }

    scenario("login by user oauth") {
      Given("an valid code and source")
      val code: String = "11123dasdasd22131123dad"
      val source: String = "1"
      (mockAccountManager.loginByWeixin _).expects(code, source).returning(Future(yunkaiUserInfo)).once

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
      (mockAccountManager.createUser _).expects(nickName, password, Some(tel)).returning(Future(null))

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

      (mockAccountManager.createUser _).expects(nickName, password, Some(userInfo.tel)).returning(Future(userInfo))

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

  feature("GroupManager can get chat groups") {
    scenario("empty chatGroupIdList is provided") {
      Given("an empty chatGroupIdList")
      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
      val chatGroupIdList: Seq[Long] = Seq()
      (mockGroupManager.getChatGroups _).expects(responseFields, chatGroupIdList).returning(Future(Map()))

      When("getChatGroups is invoked")
      waitFuture(userServiceHandler.getChatGroups(chatGroupIdList, Some(responseFields)))
    }

    scenario("chatGroupIdList have one invalid element") {
      Given("chatGroupIdList contains one invalid element")
      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
      val chatGroupIdList: Seq[Long] = Seq(200001)
      val fakeChatGroupId: Long = 200001
      val chatGroupMap: Map[Long, Option[com.lvxingpai.yunkai.model.ChatGroup]] = Map(
        fakeChatGroupId -> None
      )
      (mockGroupManager.getChatGroups _).expects(responseFields, chatGroupIdList).returning(Future(chatGroupMap))

      When("getChatGroups is invoked")
      waitFuture(userServiceHandler.getChatGroups(chatGroupIdList, Some(responseFields)))
    }

    scenario("chatGroupIdList have two elements, one is valid element, the other is invalid") {
      Given("chatGroupIdList contains one invalid element, one valid element")
      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
      val chatGroupIdList: Seq[Long] = Seq(200000, 200001)
      val fakeChatGroupId: Long = 200001
      val chatGroupMap: Map[Long, Option[com.lvxingpai.yunkai.model.ChatGroup]] = Map(
        chatGroupId -> Some(chatGroup),
        fakeChatGroupId -> None
      )
      (mockGroupManager.getChatGroups _).expects(responseFields, chatGroupIdList).returning(Future(chatGroupMap))

      When("getChatGroups is invoked")
      waitFuture(userServiceHandler.getChatGroups(chatGroupIdList, Some(responseFields)))
    }

    scenario("a invalid chatGroupId provided, get None") {
      Given("a chatGroupId")
      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
      val fakeChatGroupId: Long = 200001
      (mockGroupManager.getChatGroup _).expects(fakeChatGroupId, responseFields).returning(Future(None))

      When("getChatGroup is invoked")
      intercept[NotFoundException] {
        waitFuture(userServiceHandler.getChatGroup(fakeChatGroupId, Some(responseFields)))
      }
    }

    scenario("a valid chatGroupId provided, get chat group info") {
      Given("a chatGroupId")
      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
      val fakeChatGroupId: Long = 200000
      (mockGroupManager.getChatGroup _).expects(fakeChatGroupId, responseFields).returning(Future(Some(chatGroup)))

      When("getChatGroup is invoked")
      waitFuture(userServiceHandler.getChatGroup(fakeChatGroupId, Some(responseFields)))
    }
  }

  feature("GroupManager can update chat group info") {
    scenario("a invalid chatGroupId provided, get None") {
      Given("a invalid chatGroupId")
      val responseFields: Map[ChatGroupProp, String] = Map(
        ChatGroupProp.Name -> "updateStr"
      )
      val fakeChatGroupId: Long = 200001
      val operatorId: Long = 100004
      (mockGroupManager.updateChatGroup _).expects(fakeChatGroupId, operatorId, *).returning(Future(None))

      When("updateChatGroup is invoked")
      intercept[NotFoundException] {
        waitFuture(userServiceHandler.updateChatGroup(fakeChatGroupId, operatorId, responseFields))
      }
    }

    scenario("a valid chatGroupId provided, get chat group info") {
      Given("a valid chatGroupId")
      val responseFields: Map[ChatGroupProp, String] = Map(
        ChatGroupProp.Name -> "updateStr"
      )
      val fakeChatGroupId: Long = 200000
      val operatorId: Long = 100004
      (mockGroupManager.updateChatGroup _).expects(fakeChatGroupId, operatorId, *).returning(Future(Some(chatGroup)))

      When("updateChatGroup is invoked")
      waitFuture(userServiceHandler.updateChatGroup(fakeChatGroupId, operatorId, responseFields))
    }
  }

  feature("GroupManager can get user's chat group list") {

    //    scenario("userId is not exsits") {
    //      val fakeUserId: Long = 100000
    //      userServiceHandler.groupManager = mockGroupManager
    //      (mockGroupManager.getUserChatGroups _).expects(fakeUserId, *, *, *).returning(Future(null))
    //      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
    //      When("getUserChatGroups is invoked")
    //      intercept[NotFoundException] {
    //        waitFuture(userServiceHandler.getUserChatGroups(fakeUserId, Some(responseFields), None, None))
    //      }
    //    }

    //    scenario("chat group list is empty") {
    //      Given("a valid userId")
    //      userServiceHandler.groupManager = mockGroupManager
    //      (mockGroupManager.getUserChatGroups _).expects(haiziId, *, *, *).returning(Future(Seq()))
    //      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
    //
    //      When("getUserChatGroups is invoked")
    //      waitFuture(userServiceHandler.getUserChatGroups(haiziId, Some(responseFields), None, None))
    //    }

    scenario("chat group list is nonempty") {
      Given("a valid userId")
      (mockGroupManager.getUserChatGroups _).expects(xiaoyaoId, *, *, *).returning(Future(Seq(chatGroup)))
      val responseFields: Seq[ChatGroupProp] = Seq(ChatGroupProp.ChatGroupId, ChatGroupProp.Name, ChatGroupProp.Participants)
      When("getUserChatGroups is invoked")
      waitFuture(userServiceHandler.getUserChatGroups(xiaoyaoId, Some(responseFields), None, None))
    }
  }

  feature("GroupManager can create chat group") {
    //    scenario("creator not exists or member is not exists") {
    //      Given("a invalid chatGroupId")
    //      val invalidCreator: Long = 1000000
    //      val invalidMember: Long = 100001
    //      (mockGroupManager.createChatGroup _).expects(invalidCreator, Seq(fakeUserId), *).returning(Future(null))
    //      (mockGroupManager.createChatGroup _).expects(fakeUserId, Seq(invalidMember), *).returning(Future(null))
    //      When("createChatGroup is invoked")
    //      intercept[NotFoundException] {
    //        waitFuture(userServiceHandler.createChatGroup(invalidCreator, Seq(fakeUserId), Some(Map(ChatGroupProp.Name -> "test chat Group"))))
    //      }
    //      intercept[NotFoundException] {
    //        waitFuture(userServiceHandler.createChatGroup(fakeUserId, Seq(invalidMember), Some(Map(ChatGroupProp.Name -> "test chat Group"))))
    //      }
    //    }
    scenario("creator id and member ids are valid") {
      Given("a valid creator id and valid member id")
      (mockGroupManager.createChatGroup _).expects(fakeUserId, Seq(xiaoyaoId), *).returning(Future(chatGroup))

      When("createChatGroup is invoked")
      waitFuture(userServiceHandler.createChatGroup(fakeUserId, Seq(xiaoyaoId), Some(Map(ChatGroupProp.Name -> "test chat Group"))))
    }
  }

  val id: ObjectId = new ObjectId()
  feature("AccountManager can send contact request") {
    //    scenario("sender id  not exists or request id  not exists") {
    //      Given("a invalid sender id or requestId")
    //      val invalidId: Long = 100000
    //      (mockAccountManager.sendContactRequest _).expects(invalidId, xiaoyaoId, *).returning(Future(null))
    //      (mockAccountManager.sendContactRequest _).expects(xiaoyaoId, invalidId, *).returning(Future(null))
    //
    //      When("sendContactRequest is invoked")
    //      intercept[NotFoundException] {
    //        mockAccountManager.sendContactRequest(invalidId, xiaoyaoId, Some("宇宙无敌超级美少女雅典娜二代"))
    //      }
    //      intercept[NotFoundException] {
    //        mockAccountManager.sendContactRequest(xiaoyaoId, invalidId, Some("宇宙无敌超级美少女雅典娜二代"))
    //      }
    //    }

    scenario("valid sender id and request id are provided") {
      Given("a valid sender id and a valid request id")
      (mockAccountManager.sendContactRequest _).expects(fakeUserId, xiaoyaoId, *).returning(Future(id))

      When("sendContactRequest is invoked")
      mockAccountManager.sendContactRequest(fakeUserId, xiaoyaoId, Some("宇宙无敌超级美少女雅典娜二代"))
    }
  }

  feature("AccountManager can get contact requests") {
    //    scenario("user id not exists") {
    //      Given("a invalid user id")
    //      val invalidId: Long = 100000
    //      (mockAccountManager.getContactRequestList _).expects(invalidId, 0, 50).returning(Future(null))
    //
    //      When("getContactRequestList is invoked")
    //      intercept[NotFoundException] {
    //        mockAccountManager.getContactRequestList(invalidId, 0, 50)
    //      }
    //    }

    scenario("user id is provided") {
      Given("a valid user id")
      val contactRequest = com.lvxingpai.yunkai.model.ContactRequest(xiaoyaoId, fakeUserId, Some("宇宙无敌超级美少女雅典娜二代"), None)
      (mockAccountManager.getContactRequestList _).expects(fakeUserId, 0, 50).returning(Future(Seq(contactRequest)))

      When("getContactRequestList is invoked")
      mockAccountManager.getContactRequestList(fakeUserId, 0, 50)
    }
  }

  feature("AccountManager can update user roles") {

    //    scenario("user id not exists") {
    //      Given("a invalid user id")
    //      val roles = Seq(Role.get(10).get)
    //      val invalidId: Long = 100000
    //      (mockAccountManager.updateUserRoles _).expects(invalidId, true, roles).returning(Future(null))
    //
    //      When("updateUserRoles is invoked")
    //      intercept[NotFoundException] {
    //        mockAccountManager.updateUserRoles(invalidId, true, roles)
    //      }
    //    }

    scenario("a user id is provided") {
      Given("a valid user id")
      val roles = Seq(Role.get(10).get)
      (mockAccountManager.updateUserRoles _).expects(fakeUserId, true, roles).returning(Future(user))

      When("updateUserRoles is invoked")
      mockAccountManager.updateUserRoles(fakeUserId, true, roles)
    }
  }

  feature("AccountManager can check validation code") {
    //    scenario("tel is not exsits") {
    //      Given("a invalid tel")
    //      val code = "7432"
    //      val operationCode = OperationCode.get(1).get
    //      val tel = "18911111111"
    //      (mockAccountManager.checkValidationCode _).expects(code, operationCode, tel, None).returning(Future(None))
    //
    //      When("checkValidationCode is invoked")
    //      intercept[ValidationCodeException] {
    //        mockAccountManager.checkValidationCode(code, operationCode, tel, None)
    //      }
    //    }

    scenario("tel is provided") {
      Given("a valid user id")
      val code = "7432"
      val operationCode = OperationCode.get(1).get
      val token = "success"
      (mockAccountManager.checkValidationCode _).expects(code, operationCode, user.tel, None).returning(Future(Some(token)))

      When("checkValidationCode is invoked")
      mockAccountManager.checkValidationCode(code, operationCode, user.tel, None)
    }
  }
}
