package com.lvxingpai.yunkai

import com.lvxingpai.yunkai.handler.UserServiceHandler
import com.lvxingpai.yunkai.service.MorphiaFactory
import com.twitter.util.TimeConversions._
import com.twitter.util.{Await, Duration, Future}
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen, ShouldMatchers}

import scala.collection.JavaConversions._
import scala.language.postfixOps

/**
 * Created by zephyre on 6/22/15.
 */
class YunkaiBaseTest extends FeatureSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfter {
  var initialUsers: Seq[(UserInfo, String)] = Seq()

  // basic user info properties
  val properties = Seq(UserInfoProp.UserId, UserInfoProp.NickName)

  // assert the runlevel
  val runlevel = {
    val l = Global.conf.getString("runlevel")
    assert(l == "test")
  }

  /**
   * Drop all the account-related collections
   */
  def cleanDatabase(): Unit = {
    val ds = MorphiaFactory.datastore
    val morphia = MorphiaFactory.morphia
    morphia.getMapper.getMappedClasses map (_.getClazz) foreach (cls => {
      ds.delete(ds.createQuery(cls))
    })
  }

  def createInitUsers(): Seq[(UserInfo, String)] = {
    val userNames = Seq("Lorienna", "Thomas", "Manon", "Vivien", "Vera")
    val service = new UserServiceHandler()
    val future = Future.collect(userNames.zipWithIndex map (entry => {
      val (name, index) = entry
      val password = f"password$index%02d"
      val tel = f"138001380$index%02d"
      service.createUser(name, password, Some(Map(UserInfoProp.Tel -> tel))) map (_ -> password)
    }))
    waitFuture(future)
  }

  /**
   * Wait for a future object and return its result
   */
  def waitFuture[T](future: Future[T], timeout: Duration = 60 seconds): T = Await.result(future, timeout)

}
