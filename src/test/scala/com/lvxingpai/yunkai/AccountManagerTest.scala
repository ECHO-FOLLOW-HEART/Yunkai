package com.lvxingpai.yunkai

import java.util.UUID

import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.lvxingpai.yunkai.model.{ChatGroup => ChatGroupMorphia, UserInfo => UserInfoMorphia}
import com.twitter.util.Future

import scala.language.postfixOps
import scala.util.Random

/**
 * Created by zephyre on 6/21/15.
 */
class AccountManagerTest extends YunkaiBaseTest {

  def defineContacts(userList: Seq[(UserInfo, String)]): Unit = {
    val selfId = (userList head)._1.userId
    val contactIds = userList.slice(1, 3) map (_._1.userId)
    waitFuture(new UserServiceHandler().addContacts(selfId, contactIds))
  }

  before {
    cleanDatabase()
    initialUsers = createInitUsers()
    defineContacts(initialUsers)
  }

  after {
    cleanDatabase()
  }

  feature("the AccountManager can create new users") {
    scenario("the user already exits") {
      Given("a user-signup form")
      val userInfo = initialUsers.head._1
      val password = initialUsers.head._2

      When("createUser is invoked")
      intercept[UserExistsException] {
        waitFuture(new UserServiceHandler().createUser(userInfo.nickName, password,
          Some(Map(UserInfoProp.Tel -> userInfo.tel.get))))
      }
    }

    scenario("a cell phone number is provided") {
      Given("a user-signup form")
      val nickName = "Haizi"
      val password = "123456"
      val tel = "13800139000"

      When("createUser is invoked")
      val newUser = waitFuture(new UserServiceHandler().createUser(nickName, password, Some(Map(UserInfoProp.Tel -> tel))))

      Then("the user should be created")
      newUser.userId should be > 0L
      newUser.nickName should be(nickName)
      newUser.tel.get should be(tel)

      And("one can use the cell phone number and password to login")
      val loginUser = waitFuture(new UserServiceHandler().login(tel, password))
      loginUser.userId should be(newUser.userId)
      loginUser.nickName should be(newUser.nickName)
    }

    scenario("a WeChat OAuth account is provided") {
      pending
    }
  }

  feature("the AccountManager can log in a user") {
    scenario("the cell phone number and the password are provided") {
      initialUsers foreach (entry => {
        val (userInfo, password) = entry
        val actual = waitFuture(new UserServiceHandler().login(userInfo.tel.get, password))
        actual.userId should be(userInfo.userId)
        actual.nickName should be(userInfo.nickName)
        actual.tel.get should be(userInfo.tel.get)
      })
    }
    scenario("either the cell phone number or the password is incorrect") {
      initialUsers foreach (entry => {
        val (userInfo, password) = entry
        intercept[AuthException] {
          waitFuture(new UserServiceHandler().login(userInfo.tel.get, password + "false"))
        }
        intercept[AuthException] {
          waitFuture(new UserServiceHandler().login(userInfo.tel.get + "false", password))
        }
      })
    }
    scenario("the WeChat OAuth account is provided") {
      pending
    }
  }

  feature("the AccountManager can reset a user's password") {
    scenario("the old password is incorrect") {
      pending
    }

    scenario("the old password is correct") {
      pending
    }
  }

  feature("the AccountManager can reset a user's cell phone number") {
    scenario("the secret code is incorrect") {
      pending
    }
    scenario("the secret code is correct") {
      pending
    }
  }

  feature("the AccountManager can update a user's information") {
    scenario("the user does not exist") {
      val fakeUserId = (initialUsers map (_._1.userId)).max + 1
      intercept[NotFoundException] {
        waitFuture(new UserServiceHandler().updateUserInfo(fakeUserId, Map(UserInfoProp.NickName -> "foobar")))
      }
    }
    scenario("update a user's information") {
      var updatedUser: UserInfo = null
      val service = new UserServiceHandler()

      Given("the user's userId")
      val userInfo = initialUsers.head._1

      When("updating the nick name")
      val newNickName = "new nick %d" format System.currentTimeMillis()
      updatedUser = waitFuture(service.updateUserInfo(userInfo.userId, Map(UserInfoProp.NickName -> newNickName)))
      Then("the nick name should be updated successfully")
      updatedUser.nickName should be(newNickName)

      When("updating the avatar")
      val avatar = UUID.randomUUID().toString
      updatedUser = waitFuture(service.updateUserInfo(userInfo.userId, Map(UserInfoProp.Avatar -> avatar)))
      Then("the avatar should be updated successfully")
      updatedUser.avatar.get should be(avatar)

      When("updating the signature")
      val signature = Random.nextString(32)
      updatedUser = waitFuture(service.updateUserInfo(userInfo.userId, Map(UserInfoProp.Signature -> signature)))
      Then("the signature should be updated successfully")
      updatedUser.signature.get should be(signature)

      When("updating the user's gender")
      Then("the gender should be updated successfully")
      Seq("m", "f", null) foreach (gender => {
        val genderValue: Option[Gender] = gender match {
          case "m" => Some(Gender.Male)
          case "f" => Some(Gender.Female)
          case null => None
        }
        updatedUser = waitFuture(service.updateUserInfo(userInfo.userId, Map(UserInfoProp.Gender -> gender)))
        updatedUser.gender should be(genderValue)
      })
    }
  }

  feature("the AccountManager can get a user's details") {
    scenario("an invalid user ID is provided") {
      val userId = (initialUsers map (_._1.userId) max) + 1
      intercept[NotFoundException] {
        waitFuture(new UserServiceHandler().getUserById(userId, Some(Seq(UserInfoProp.UserId))))
      }
    }
    scenario("the user's nick name, ID, avatar and cell phone number are requested") {
      val userInfo = (initialUsers head)._1
      val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar, UserInfoProp.Tel)
      val actual = waitFuture(new UserServiceHandler().getUserById(userInfo.userId, Some(properties)))
      actual.userId should be(userInfo.userId)
      actual.nickName should be(userInfo.nickName)
      actual.avatar should be(userInfo.avatar)
      actual.tel should be(userInfo.tel)
      actual.signature should be(None)
      actual.gender should be(None)
    }
    scenario("multiple user's Ids are provided") {
      Given("a user ID list mixed with a fake user ID")
      val initialUserMap = Map(initialUsers map (entry => entry._1.userId -> entry._1): _*)
      val fakeUserId = (initialUsers map (_._1.userId) max) + 1
      val userIdList = (initialUsers map (_._1.userId)) :+ fakeUserId

      When("retrive the users' details according to the user ID lsit")
      val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar, UserInfoProp.Tel)
      val userMap = waitFuture(new UserServiceHandler().getUsersById(userIdList, Some(properties)))

      Then("the user corresponding to the fake user ID should be null")
      userMap(fakeUserId) should be(null)

      And("the details of other users should be correctly retrieved")
      initialUserMap foreach (entry => {
        val (userId, userInfo) = entry
        val actual = userMap(userId)
        actual.userId should be(userInfo.userId)
        actual.nickName should be(userInfo.nickName)
        actual.avatar should be(userInfo.avatar)
        actual.tel should be(userInfo.tel)
        actual.signature should be(None)
        actual.gender should be(None)
      })
    }
  }

  feature("the AccountManager can get the number of a user's contacts") {
    scenario("an invalid user ID is provided") {
      val fakeUserId = (initialUsers map (_._1.userId) max) + 1
      intercept[NotFoundException] {
        waitFuture(new UserServiceHandler().getContactCount(fakeUserId))
      }
    }
    scenario("the correct user ID is provided") {
      val userId = (initialUsers head)._1.userId
      val cnt = waitFuture(new UserServiceHandler().getContactCount(userId))
      cnt should be(2)
    }
  }

  feature("the AccountManager can get a user's contact list") {
    scenario("an invalid user ID is provided") {
      val userId = (initialUsers map (_._1.userId) max) + 1
      val contactList = waitFuture(new UserServiceHandler().getContactList(userId, Some(Seq(UserInfoProp.UserId)), None, None))
      contactList shouldBe empty
    }
    scenario("the user's contact list is returned") {
      val userId = (initialUsers head)._1.userId
      val contacts = initialUsers.slice(1, 3) map (_._1)
      val contactList = waitFuture(new UserServiceHandler().getContactList(userId,
        Some(Seq(UserInfoProp.UserId, UserInfoProp.NickName, UserInfoProp.Avatar)), None, None))
      contactList should have length 2
      contacts.zipWithIndex foreach (entry => {
        val (userInfo, index) = entry
        val actual = contactList(index)
        actual.userId should be(userInfo.userId)
        actual.nickName should be(userInfo.nickName)
        actual.avatar should be(userInfo.avatar)
      })
    }
    scenario("the number of elements of the user's contact list can be limited") {
      val userId = (initialUsers head)._1.userId
      val contactList = waitFuture(new UserServiceHandler().getContactList(userId, Some(Seq(UserInfoProp.UserId)),
        Some(0), Some(1)))
      contactList should have length 1
    }
  }

  feature("the AccountManager can add contacts to a user") {
    scenario("an invalid user ID is provided") {
      val service = new UserServiceHandler()
      val targetUser = (initialUsers last)._1

      Given("an incorrect user ID")
      val fakeUserId = (initialUsers map (_._1.userId) max) + 1
      When("adding contacts")
      Then("an exception indicating the user absence should be thrown")
      intercept[NotFoundException] {
        waitFuture(service.addContact(fakeUserId, targetUser.userId))
      }
      intercept[NotFoundException] {
        waitFuture(service.addContacts(fakeUserId, Seq(targetUser.userId)))
      }
      intercept[NotFoundException] {
        waitFuture(service.addContact(targetUser.userId, fakeUserId))
      }
      intercept[NotFoundException] {
        waitFuture(service.addContacts(targetUser.userId, Seq(fakeUserId)))
      }
    }
    scenario("a single contact is added") {
      val users = initialUsers.slice(1, 3) map (_._1)
      val userA = users head
      val userB = users last
      val service = new UserServiceHandler()
      waitFuture(service.addContact(users.head.userId, users.last.userId))

      Seq((userA, userB), (userB, userA)) foreach (entry => {
        val (u1, u2) = entry
        val contactList = waitFuture(service.getContactList(u1.userId, Some(Seq(UserInfoProp.UserId)), None, None))
        (contactList map (_.userId)) should contain(u2.userId)
      })
    }
    scenario("multiple contacts are added simultaneously") {
      val targets = (initialUsers init) map (_._1)
      val self = (initialUsers last)._1
      val service = new UserServiceHandler()

      waitFuture(service.addContacts(self.userId, targets map (_.userId)))
      val contactList = waitFuture(service.getContactList(self.userId, Some(Seq(UserInfoProp.UserId)), None, None))
      val targetIds = targets map (_.userId)
      contactList map (_.userId) should contain allOf(targetIds head, targetIds(1), targetIds drop 2: _*)
    }
    scenario("a contact is added multiple times") {
      val service = new UserServiceHandler()
      val self = (initialUsers head)._1
      val originalContacts = waitFuture(service.getContactList(self.userId, Some(Seq(UserInfoProp.UserId)), None, None))
      val target = originalContacts head

      waitFuture(service.addContact(self.userId, target.userId))

      val thisContacts = waitFuture(service.getContactList(self.userId, Some(Seq(UserInfoProp.UserId)), None, None))
      val idList1 = originalContacts map (_.userId)
      val idList2 = thisContacts map (_.userId)
      idList1 should contain allOf(idList2 head, idList2(1), idList2 drop 2: _*)
    }
  }

  feature("the AccountManager can remove contacts to a user") {
    scenario("an invalid user ID is provided") {
      val service = new UserServiceHandler()
      val targetUser = (initialUsers last)._1

      Given("an incorrect user ID")
      val fakeUserId = (initialUsers map (_._1.userId) max) + 1
      When("adding contacts")
      Then("an exception indicating the user absence should be thrown")
      intercept[NotFoundException] {
        waitFuture(service.removeContact(fakeUserId, targetUser.userId))
      }
      intercept[NotFoundException] {
        waitFuture(service.removeContacts(fakeUserId, Seq(targetUser.userId)))
      }
      intercept[NotFoundException] {
        waitFuture(service.removeContact(targetUser.userId, fakeUserId))
      }
      intercept[NotFoundException] {
        waitFuture(service.removeContacts(targetUser.userId, Seq(fakeUserId)))
      }
    }
    scenario("a single contact is removed") {
      val service = new UserServiceHandler()
      val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName)
      val allUsers = initialUsers map (_._1)
      val (self, targets) = (allUsers last, allUsers init)

      waitFuture(service.addContacts(self.userId, targets map (_.userId)))
      val originalContacts = waitFuture(service.getContactList(self.userId, Some(properties), None, None))
      Given("that %s has %s as the contact list" format(self.nickName, originalContacts map (_.nickName) mkString ", "))

      val userToDel = originalContacts.head
      When("%s is removed from %s's contact list" format(userToDel.nickName, self.nickName))
      waitFuture(service.removeContact(self.userId, userToDel.userId))

      Then("%s's contact list should not contain %s, and vice versa" format(self.nickName, userToDel.nickName))
      // retrieve the contact lists of self and userToDel
      val ret = waitFuture(Future.collect(Seq(self, userToDel) map (u => service.getContactList(u.userId, Some(properties),
        None, None))))
      val idListSelf = (ret head) map (_.userId)
      val idListTarget = (ret last) map (_.userId)
      idListSelf should not contain userToDel.userId
      idListTarget should not contain self.userId
    }
  }

  feature("the AccountManager can test if two users are contacts") {
    scenario("default") {
      val service = new UserServiceHandler()
      val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName)

      val self = (initialUsers head)._1
      val targets = Map((initialUsers tail) map (u => u._1.userId -> u._1): _*)
      val contacts = Map(waitFuture(service.getContactList(self.userId, Some(properties), None, None)) map (u => u.userId -> u): _*)
      val outsiders = targets filterKeys (userId => !(contacts contains userId))

      Given("%s and its contact list %s" format(self.nickName, contacts.values map (_.nickName) mkString ", "))
      When("testing their relationship")
      Then("bolean results should be returned")

      // contact userId pairs and stranger pairs
      def mkPairs(idList: Seq[Long]): Seq[(Long, Long)] = idList flatMap (v => Seq(self.userId -> v, v -> self.userId))
      val (contactPairs, strangerPairs) = (mkPairs(contacts.keySet.toSeq), mkPairs(outsiders.keySet.toSeq))

      Seq(contactPairs -> true, strangerPairs -> false) foreach (entry => {
        val pairs = entry._1
        val relation = entry._2
        val future = Future.collect(pairs map (p => service.isContact(p._1, p._2)))
        waitFuture(future) should contain only relation
      })
    }
  }
}
