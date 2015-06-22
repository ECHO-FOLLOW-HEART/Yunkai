import com.lvxingpai.yunkai
import com.lvxingpai.yunkai.UserInfoProp
import com.lvxingpai.yunkai.database.mongo.MorphiaFactory
import com.lvxingpai.yunkai.model._
import org.specs2.specification.core.SpecStructure

import scala.util.Random

/**
 * Created by zephyre on 6/18/15.
 */
class AccountManagerTest extends YunkaiBaseTest {

  def cleanDatabase(): Unit = {
    val ds = MorphiaFactory.datastore
    Seq(classOf[UserInfo], classOf[ChatGroup], classOf[Conversation], classOf[Credential],
      classOf[Relationship], classOf[Sequence]) foreach (cls => {
        ds.delete(ds.createQuery(cls))
      })
  }

  override def beforeAll(): Unit = {
    cleanDatabase()
    server = createServer()
    client = createClient()
    createUser
  }

  def createUser = {
    val userInfoList = userPreset.toSeq map (entry => {
      val name = entry._1
      val data = entry._2
      val password = data("password")
      val tel = data("tel")
      val user = invoke(client.createUser(name, password, Some(tel)))
      userPreset(name)("userId") = user.userId.toString
      name -> user
    })

    userInfoList map (entry => {
      val (nickName, userInfo) = entry
      userInfo.userId.toInt must beGreaterThan(1)
      userInfo.nickName must_=== nickName
    })
  }

  override def is: SpecStructure =
    s2"""
        |This is a specification to check AccountManager
        |
        |The AccountManager should:
        |can login                     $login
        |can reset passwords           $resetPasswords
        |can get user info             $userInfo
        |can update user info          $updateUserInfo
        |can manipulate contacts       $checkContacts
      """.stripMargin

  def login = {
    userPreset.toSeq map (entry => {
      val name = entry._1
      val password = entry._2("password")
      val tel = entry._2("tel")
      val userId = entry._2("userId").toLong

      val user = invoke(client.login(tel, password))
      user.userId.toString must_=== userId.toString
      user.nickName must_=== name
    })
  }

  def resetPasswords = {
    val (nickName, userInfo) = userPreset.toSeq.head
    val userId = userInfo("userId").toLong
    val loginName = userInfo("tel")
    val newPassword = Random.nextString(8)

    invoke(client.updatePassword(userId, newPassword))
    val newUser = invoke(client.login(loginName, newPassword))

    newUser.userId must_=== userId
    newUser.nickName must_=== nickName
  }

  def userInfo = {
    userPreset.toSeq map (entry => {
      val name = entry._1
      val userDefined = entry._2
      val userId = userDefined("userId").toLong
      val tel = userDefined("tel")

      val actual = invoke(client.getUserById(userId))
      actual.nickName must_=== name
      actual.userId.toString must_=== userId.toString
      actual.tel.get must_=== tel
    })
  }

  def updateUserInfo = {
    userPreset.toSeq map (entry => {
      val userDefined = entry._2
      val userId = userDefined("userId").toLong
      val newSig = Random.nextString(32)

      invoke(client.updateUserInfo(userId, Map(UserInfoProp.Signature -> newSig)))

      val actual = invoke(client.getUserById(userId))
      actual.signature.get must_=== newSig
    })
  }

  def checkContacts = {
    val targetName = "Haizi"
    val contactNames = Seq("CK", "Xiaoyao", "Xigua")
    val removedNames = Seq("Xiaoyao")
    val finalNames = Seq("CK", "Xigua")

    val fields = Seq(UserInfoProp.UserId, UserInfoProp.NickName)
    val targetUser = invoke(client.getUserById(userPreset(targetName)("userId").toLong))
    val contactUsers = invoke(client.getMultipleUsers(contactNames map (n => userPreset(n)("userId").toLong), fields))
    val removedUsers = invoke(client.getMultipleUsers(removedNames map (n => userPreset(n)("userId").toLong), fields))
    val finalUsers = invoke(client.getMultipleUsers(finalNames map (n => userPreset(n)("userId").toLong), fields))

    val contacts1 = invoke(client.getContactList(targetUser.userId, Some(fields), None, None))
    contacts1 must beEmpty

    def helperFunc(userList: Seq[yunkai.UserInfo]): String = (userList map (_.userId)).toSet.toSeq.sorted mkString "|"

    // Add contacts
    val contactUserIds = contactUsers.values.toSeq.filter(_ != null).map(_.userId)
    invoke(client.addContacts(targetUser.userId, contactUserIds))
    val contacts2 = invoke(client.getContactList(targetUser.userId, Some(fields), None, None))
    helperFunc(contacts2) must_=== helperFunc(contactUsers.values.toSeq)

    // Remove contacts
    val removedUserIds = removedUsers.values.toSeq.filter(_ != null).map(_.userId)
    invoke(client.removeContacts(targetUser.userId, removedUserIds))
    val contacts3 = invoke(client.getContactList(targetUser.userId, Some(fields), None, None))
    helperFunc(contacts3) must_=== helperFunc(finalUsers.values.toSeq)
  }
}
