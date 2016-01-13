package com.lvxingpai.yunkai

import java.util.{ Date, List => JList, UUID }

import com.fasterxml.jackson.databind.node.{ LongNode, TextNode }
import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.lvxingpai.yunkai.model.{ ContactRequest => DBContactRequest }
import com.twitter.util.FuturePool
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.util.Try

/**
 * Created by zephyre on 5/19/15.
 */
object Implicits {
  implicit lazy val defaultFuturePool = FuturePool.unboundedPool

  implicit lazy val defaultExecutionContext = scala.concurrent.ExecutionContext
    .fromExecutorService(defaultFuturePool.executor)

  object YunkaiConversions {
    implicit def contactRequestConversion(req: DBContactRequest): ContactRequest = {
      ContactRequest(req.id.toString, req.sender, req.receiver, req.status, req.requestMessage,
        req.rejectMessage, req.timestamp, req.expire)
    }

    /**
     * SecretKey隐式转换: morphia -> thrift
     */
    implicit def secretKeyConversion(src: model.SecretKey): SecretKey =
      SecretKey(src.key, src.timestamp.getTime, Option(src.expire) map (_.getTime))

    /**
     * SecretKey隐式转换: thrift -> morphia
     */
    implicit def secretKeyConversion(src: SecretKey): model.SecretKey = {
      val result = new model.SecretKey
      result.key = src.key
      result.timestamp = new Date(src.timestamp)

      if (src.expire.nonEmpty) {
        result.expire = new Date(src.expire.get)
      }

      result
    }

    implicit def genderConversion(value: Gender): String = value.value match {
      case Gender.Male.value => "m"
      case Gender.Female.value => "f"
      case Gender.Secret.value => "s"
      case Gender.Both.value => "b"
    }

    implicit def genderConversion(value: String): Gender = value.toLowerCase() match {
      case "m" => Gender.Male
      case "f" => Gender.Female
      case "s" => Gender.Secret
      case "b" => Gender.Both
      case "u" => null
    }

    implicit def userConversion(user: model.UserInfo): UserInfo = {
      // 处理tel为UUID占位符的情况
      val tel = Option(user.tel) flatMap (value => if (value.length == 36) Some(value) else None)
      val gender = Option(user.gender) flatMap (value => Option(genderConversion(value)))
      val memo = Option(user.memo)
      val roles = Option(user.roles) map (_.toSeq) getOrElse Seq() map (value => {
        Try(Role(value)).toOption
      }) filter (_.nonEmpty) map (_.get)
      val residence = Option(user.residence)
      val birthday = Option(user.birthday)

      val secretKey = Option(user.secretKey) map secretKeyConversion

      UserInfo(user.id.toString, user.userId, user.nickName, Option(user.avatar), signature = Option(user.signature),
        roles = roles, memo = memo, gender = gender, tel = tel, loginStatus = false, birth = birthday,
        residence = residence, secretKey = secretKey)
    }

    implicit def userConversion(user: UserInfo): model.UserInfo = {
      val user2 = model.UserInfo(user.userId, user.nickName)
      user2.id = Option(user.id) map (new ObjectId(_)) getOrElse new ObjectId()
      user2.avatar = user.avatar.orNull
      user2.signature = user.signature.orNull
      user2.tel = user.signature getOrElse UUID.randomUUID().toString
      user2.gender = (user.gender map (_.name)).orNull
      user2.residence = user.residence.orNull
      user2.birthday = user.birth.orNull
      if (user.secretKey.nonEmpty) {
        user2.secretKey = user.secretKey.get
      }
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
