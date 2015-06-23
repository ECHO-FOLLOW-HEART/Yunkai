package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.UserServiceHandler

/**
 * Created by zephyre on 6/23/15.
 */
class GroupManagerTest extends YunkaiBaseTest {
  val service = new UserServiceHandler()

  var initialChatGroups: Seq[ChatGroup] = null

  def createChatGroups(): Unit = {

  }

  before {
    cleanDatabase()
    initialUsers = createInitUsers()
  }

  after {
    cleanDatabase()
  }

}
