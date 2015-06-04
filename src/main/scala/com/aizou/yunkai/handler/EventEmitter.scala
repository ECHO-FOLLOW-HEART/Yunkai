package com.aizou.yunkai.handler

import com.aizou.yunkai.AppConfig
import com.fasterxml.jackson.databind.JsonNode
import com.lvxingpai.apium.ApiumPlant.ConnectionParam
import com.lvxingpai.apium.{ ApiumPlant, ApiumSeed }

/**
 * 事件发布模块
 *
 * Created by zephyre on 6/2/15.
 */
object EventEmitter {

  /**
   * 注册新用户的事件
   */
  val evtCreateUser = "createUser"

  /**
   * 重置用户密码的事件
   */
  val evtResetPassword = "resetPassword"

  /**
   * 用户登录的事件
   */
  val evtLogin = "login"

  /**
   * 用户退出登录的事件
   */
  val evtLogout = "logout"

  /**
   * 添加/删除联系人的事件
   */
  val evtModContacts = "modContacts"

  /**
   * 发送好友申请的事件
   */
  val evtContactReq = "contactReq"

  /**
   * 添加/移除黑名单的事件
   */
  val evtModBlockList = "modBlocList"

  /**
   * 关注某个用户的事件
   */
  val evtFollow = "follow"

  /**
   * 修改个人信息事件
   */
  val evtModUserInfo = "modUserInfo"

  /**
   * 创建讨论组的事件
   */
  val evtCreateChatGroup = "createChatGroup"

  /**
   * 添加/删除讨论组成员的事件
   */
  val evtModGroupMembers = "modGroupMembers"

  /**
   * 修改讨论组属性的事件
   */
  val evtModChatGroup = "modChatGroup"

  /**
   * 添加群申请事件
   */
  val evtChatGroupReq = "chatGroupReq"

  // 初始化
  val apiumPlant = {
    val conf = AppConfig.conf
    val host = conf.getString("rabbitmq.host")
    val port = conf.getInt("rabbitmq.port")
    val username = conf.getString("rabbitmq.username")
    val password = conf.getString("rabbitmq.password")
    val virtualHost = conf.getString("rabbitmq.virtualhost")
    ApiumPlant(ConnectionParam(host, port, username, password, virtualHost), "yunkai",
      Seq(evtCreateUser, evtLogin, evtResetPassword))
  }

  /**
   * 触发事件
   *
   * @param eventName 事件名称
   * @param eventArgs 事件参数。要求是一个scala.collection.immutable.Map[String, JsonNode]类型的对象
   */
  def emitEvent(eventName: String, eventArgs: Map[String, JsonNode]) {
    val eventMap = if (eventArgs != null && (eventArgs nonEmpty)) Some(eventArgs)
    else None
    val seed = ApiumSeed(apiumPlant.defaultTaskName(eventName), kwargs = eventMap)
    apiumPlant.sendSeed(eventName, seed)
  }
}
