package com.lvxingpai.yunkai.handler

import com.fasterxml.jackson.databind.JsonNode
import com.lvxingpai.apium.ApiumPlant.ConnectionParam
import com.lvxingpai.apium.{ApiumPlant, ApiumSeed}
import com.lvxingpai.yunkai.Global

import scala.language.postfixOps

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
   * 发送联系人邀请的事件
   */
  val evtSendContactRequest = "sendContactRequest"

  /**
   * 接受联系人邀请的事件
   */
  val evtAcceptContactRequest = "acceptContactRequest"

  /**
   * 拒绝联系人邀请的事件
   */
  val evtRejectContactRequest = "rejectContactRequest"

  /**
   * 添加联系人的事件
   */
  val evtAddContacts = "addContacts"

  /**
   * 删除联系人的事件
   */
  val evtRemoveContacts = "removeContacts"

  /**
   * 发送好友申请的事件
   */
  val evtContactReq = "contactReq"

  /**
   * 添加黑名单的事件
   */
  val evtAddBlockList = "addBlocList"

  /**
   * 移除黑名单的事件
   */
  val evtRemoveBlockList = "removeBlocList"

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
   * 添加讨论组成员的事件
   */
  val evtAddGroupMembers = "addGroupMembers"

  /**
   * 删除讨论组成员的事件
   */
  val evtRemoveGroupMembers = "removeGroupMembers"

  /**
   * 修改讨论组属性的事件
   */
  val evtModChatGroup = "modChatGroup"

  // 初始化
  val apiumPlant = {
    val conf = Global.conf

    // 获得rabbitmq的地址
    import scala.collection.JavaConversions._
    val tmp = conf.getConfig("backends.rabbitmq").entrySet().toSeq.head.getValue.unwrapped().toString.split(":")
    val host = tmp(0)
    val port = tmp(1).toInt

    val username = conf.getString("yunkai.rabbitmq.username")
    val password = conf.getString("yunkai.rabbitmq.password")
    val virtualHost = conf.getString("yunkai.rabbitmq.virtualhost")
    ApiumPlant(ConnectionParam(host, port, username, password, virtualHost), "yunkai",
      Seq(evtCreateUser, evtLogin, evtResetPassword, evtAddContacts, evtRemoveContacts, evtModUserInfo,
        evtCreateChatGroup, evtModChatGroup, evtAddGroupMembers, evtRemoveGroupMembers, evtSendContactRequest,
        evtAcceptContactRequest, evtRejectContactRequest))
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
