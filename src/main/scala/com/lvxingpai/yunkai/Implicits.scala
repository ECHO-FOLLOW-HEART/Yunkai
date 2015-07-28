package com.lvxingpai.yunkai

import java.util.{ List => JList, UUID }

import com.fasterxml.jackson.databind.node.{ LongNode, TextNode }
import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.lvxingpai.yunkai.model.{ ContactRequest => DBContactRequest }
import com.lvxingpai.yunkai.service.MorphiaFactory
import com.twitter.util.FuturePool
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.language.implicitConversions

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit lazy val mongoDatastore = MorphiaFactory.datastore

  implicit lazy val defaultFuturePool = FuturePool.unboundedPool

  implicit lazy val defaultExecutionContext = scala.concurrent.ExecutionContext
    .fromExecutorService(defaultFuturePool.executor)

  object YunkaiConversions {
    implicit def contactRequestConversion(req: DBContactRequest): ContactRequest = {
      ContactRequest(req.id.toString, req.sender, req.receiver, req.status, req.requestMessage,
        req.rejectMessage, req.timestamp, req.expire)
    }

    implicit def userInfoMorphia2Yunkai(user: model.UserInfo): UserInfo = {
      // 处理tel为UUID占位符的情况
      val tel = {
        if (user.tel != null && user.tel.length == 36)
          None
        else
          Option(user.tel)
      }

      val gender = Option(user.gender match {
        case "m" | "M" => Gender.Male
        case "f" | "F" => Gender.Female
        case "s" | "S" => Gender.Secret
        case "b" | "B" => Gender.Both
        case "u" | "U" | null => null
        case _ => throw new IllegalArgumentException("Invalid gender")
      })
      val memo = if (user.memo != null) Option(user.memo) else None
      val roles = Option(user.roles) map (_.toSeq map Role.apply) getOrElse Seq()

      UserInfo(user.id.toString, user.userId, user.nickName, Option(user.avatar), signature = Option(user.signature),
        roles = roles, memo = memo, gender = gender, tel = tel, loginStatus = false)
    }

    implicit def userInfoYunkai2Morphia(user: UserInfo): model.UserInfo = {
      val user2 = model.UserInfo(user.userId, user.nickName)
      user2.id = {
        val id = user.id
        if (id != null && id.nonEmpty)
          new ObjectId(id)
        else
          new ObjectId()
      }
      user2.avatar = user.avatar.orNull
      user2.signature = user.signature.orNull
      user2.tel = user.signature getOrElse UUID.randomUUID().toString
      user2
    }
  }

  object JsonConversions {
    implicit def string2JsonNode(text: String): JsonNode = TextNode.valueOf(Option(text) getOrElse "")

    implicit def long2JsonNode(value: Long): JsonNode = LongNode.valueOf(value)

    implicit def int2JsonNode(value: Int): JsonNode = LongNode.valueOf(value)

    implicit def jlist2JsonNode[T](list: JList[T]): JsonNode = new ObjectMapper().valueToTree(list)

    implicit def seq2JsonNode[T](seq: Seq[T]): JsonNode = new ObjectMapper().valueToTree(seqAsJavaList(seq))

    implicit def JsonNode2string(node: JsonNode): String = new ObjectMapper().writeValueAsString(node)

    implicit def user2JsonNode(user: com.lvxingpai.yunkai.UserInfo): JsonNode = {
      val node = new ObjectMapper().createObjectNode()
      //    targets.put("id", user.id.toString)
      node.put("userId", user.userId)
      node.put("nickName", user.nickName)
      val avatarValue = user.avatar.getOrElse[String]("")
      node.put("avatar", avatarValue)
      node
    }
  }

}
