package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.UserServiceHandler

import scala.language.postfixOps

/**
 * Created by zephyre on 6/23/15.
 */
class GroupManagerTest extends YunkaiBaseTest {
  val service = new UserServiceHandler()

  var initialChatGroups: Seq[ChatGroup] = null

  def createChatGroups(): Unit = {
    val creator = (initialUsers head)._1
    val participants = initialUsers.slice(1, 3) map (_._1)
    service.createChatGroup(creator.userId, participants map (_.userId))
  }

  before {
    cleanDatabase()
    initialUsers = createInitUsers()
  }

  after {
    cleanDatabase()
  }

}
