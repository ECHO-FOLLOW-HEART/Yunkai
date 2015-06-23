package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.UserServiceHandler

import scala.language.postfixOps

/**
 * Created by zephyre on 6/23/15.
 */
class GroupManagerTest extends YunkaiBaseTest {
  val service = new UserServiceHandler()

  var initialChatGroups: Map[Long, ChatGroup] = null

  def createChatGroups(): Map[Long, ChatGroup] = {
    val creator = (initialUsers head)._1
    val participants = initialUsers.slice(1, 3) map (_._1)
    val group = waitFuture(service.createChatGroup(creator.userId, participants map (_.userId), None))
    Map(group.chatGroupId -> group)
  }

  before {
    info("cleaning database")
    cleanDatabase()
    initialUsers = createInitUsers()
    initialChatGroups = createChatGroups()
  }

  after {
    cleanDatabase()
  }

  feature("the GroupManager can create new chat groups") {
    scenario("a simple chat group without additional information is created") {
      val creator = (initialUsers last)._1
      val participants = (initialUsers init) map (_._1)
      val group = waitFuture(service.createChatGroup(creator._1, participants map (_.userId), None))
      val actual = waitFuture(service.getChatGroup(group.chatGroupId,
        Some(Seq(ChatGroupProp.Creator, ChatGroupProp.Participants))))
      group.chatGroupId should be(actual.chatGroupId)
      group.creator should be(actual.creator)
      val members = actual.participants
      group.participants should contain allOf(members head, members(1), members drop 2: _*)
    }
    scenario("a chat group with additional information is created") {
      val creator = (initialUsers last)._1
      val participants = (initialUsers init) map (_._1)
      val miscInfo: Map[ChatGroupProp, String] = Map(
        ChatGroupProp.GroupDesc -> "Group description",
        ChatGroupProp.Name -> "Group name",
        ChatGroupProp.MaxUsers -> "200"
      )
      val group = waitFuture(service.createChatGroup(creator._1, participants map (_.userId), Some(miscInfo)))
      val actual = waitFuture(service.getChatGroup(group.chatGroupId, Some(Seq(ChatGroupProp.Creator,
        ChatGroupProp.Participants, ChatGroupProp.GroupDesc, ChatGroupProp.Name, ChatGroupProp.MaxUsers))))
      group.chatGroupId should be(actual.chatGroupId)
      group.creator should be(actual.creator)
      val members = actual.participants
      group.participants should contain allOf(members head, members(1), members drop 2: _*)
      group.groupDesc.get should be(actual.groupDesc.get)
      group.name should be(actual.name)
      group.maxUsers should be(actual.maxUsers)
    }
  }

  feature("the GroupManager can get chat group details") {
    scenario("the chat group ID is incorrect") {
      val fakeId = initialChatGroups.keySet.max + 1
      intercept[NotFoundException] {
        waitFuture(service.getChatGroup(fakeId, None))
      }
    }
    scenario("a correct chat group ID is provided") {
      val (groupId, group) = initialChatGroups head
      val actual = waitFuture(service.getChatGroup(groupId, Some(Seq(ChatGroupProp.Creator))))
      group.chatGroupId should be(actual.chatGroupId)
      group.creator should be(actual.creator)
    }
  }

  feature("the GroupManager can update chat group information") {
    scenario("the chat group ID is incorrect") {
      pending
    }
    scenario("a correct chat group ID is provided") {
      pending
    }
  }

  feature("the GroupManager can add users to a chat group") {
    scenario("the chat group ID is incorrect") {
      pending
    }
    scenario("the user's ID is incorrect") {
      pending
    }
    scenario("a single user is added") {
      pending
    }
    scenario("multiple users are added at the same time") {
      pending
    }
    scenario("the number of members exceeds the maxium") {
      pending
    }
  }

  feature("the GroupManager can remove users to a chat group") {
    scenario("the chat group ID is incorrect") {
      pending
    }
    scenario("a single user is removed") {
      pending
    }
    scenario("multiple users are removed at the same time") {
      pending
    }
  }

  feature("the GroupManager can modify a chat group's administrator list") {
    scenario("the chat group ID is incorrect") {
      pending
    }
    scenario("the authentication failed") {
      pending
    }
    scenario("the administrator list is empty") {
      pending
    }
    scenario("the number of administrators exceeds the maxium") {
      pending
    }
  }
}
