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
      val fakeId = initialChatGroups.keySet.max + 1
      intercept[NotFoundException] {
        waitFuture(service.updateChatGroup(fakeId, Map(ChatGroupProp.Name -> "new name")))
      }
    }
    scenario("a correct chat group ID is provided") {
      val (groupId, group) = initialChatGroups.head
      Given(s"a chat group $groupId")

      val name = "New group name"
      val desc = "New group desc"
      val avatar = "123456abcdef"
      val visible = "true"
      val maxUsers = "200"
      When("updateChatGroup is invoked")

      val updated = waitFuture(service.updateChatGroup(groupId, Map(
        ChatGroupProp.Name -> name,
        ChatGroupProp.GroupDesc -> desc,
        ChatGroupProp.Avatar -> avatar,
        ChatGroupProp.MaxUsers -> maxUsers,
        ChatGroupProp.Visible -> visible)))

      Then("these properties should be updated successfully")
      group.chatGroupId should be(updated.chatGroupId)
      name should be(updated.name)
      desc should be(updated.groupDesc.get)
      avatar should be(updated.avatar.get)
      visible.toBoolean should be(updated.visible)
      maxUsers.toInt should be(updated.maxUsers)
    }
  }

  feature("the GroupManager can add users to a chat group") {
    scenario("the chat group ID is incorrect") {
      val fakeId = initialChatGroups.keySet.max + 1
      intercept[NotFoundException] {
        waitFuture(service.addChatGroupMembers(fakeId, Seq(1, 2, 3)))
      }
    }
    scenario("the user's ID is incorrect") {
      val groupId = initialChatGroups.keys.head
      val fakeId = (initialUsers map (_._1.userId) max) + 1
      intercept[NotFoundException] {
        waitFuture(service.addChatGroupMembers(groupId, Seq(fakeId, fakeId + 1)))
      }
    }
    scenario("users are added to a chat group") {
      val initialUserIds = initialUsers map (_._1.userId)
      val creator = initialUserIds.head
      val initMembers = initialUserIds take 2
      val others = initialUserIds drop 2
      val group = waitFuture(service.createChatGroup(creator, initMembers, None))
      waitFuture(service.addChatGroupMembers(group.chatGroupId, others))
      val updated = waitFuture(service.getChatGroup(group.chatGroupId, Some(Seq(ChatGroupProp.Participants))))
      updated.participants should contain allOf(initMembers.head, initMembers(1), others: _*)
    }
    scenario("the number of members exceeds the maxium") {
      val group = waitFuture(service.getChatGroup(initialChatGroups.head._1, Some(Seq(ChatGroupProp.Participants))))
      val maxUser = group.participants.length
      waitFuture(service.updateChatGroup(group.chatGroupId, Map(ChatGroupProp.MaxUsers -> maxUser.toString)))
      intercept[GroupMembersLimitException] {
        waitFuture(service.addChatGroupMembers(group.chatGroupId, initialUsers map (_._1.userId)))
      }
      val newGroup = waitFuture(service.getChatGroup(initialChatGroups.head._1, Some(Seq(ChatGroupProp.Participants))))
      val oldMembers = group.participants
      newGroup.participants should contain inOrderOnly(oldMembers.head, oldMembers(1), oldMembers.drop(2): _*)
    }
  }

  feature("the GroupManager can remove users to a chat group") {
    scenario("the chat group ID is incorrect") {
      val fakeId = initialChatGroups.keySet.max + 1
      intercept[NotFoundException] {
        waitFuture(service.removeChatGroupMembers(fakeId, Seq(1, 2, 3)))
      }
    }
    scenario("users are removed from the chat group") {
      val group = initialChatGroups.values.toSeq.head
      val oldMembers = group.participants
      val removedUserIds = oldMembers.tail
      val newMembers = waitFuture(service.removeChatGroupMembers(group.chatGroupId, removedUserIds))
      val newGroup = waitFuture(service.getChatGroup(group.chatGroupId, Some(Seq(ChatGroupProp.Participants))))
      newGroup.participants should contain only oldMembers.head
      newMembers should contain only oldMembers.head
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
