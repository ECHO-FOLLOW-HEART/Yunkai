package com.lvxingpai.yunkai

import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Guice, Key }
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.etcd.EtcdStoreModule
import com.lvxingpai.morphia.MorphiaModule
import com.twitter.util.FuturePool

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
 * Created by zephyre on 6/15/15.
 */
object Global {
  val (configuration, injector) = {
    val basicInjector = Guice.createInjector(new EtcdStoreModule(Configuration.load()))
    val configuration = basicInjector.getInstance(Key.get(classOf[Configuration], Names.named("etcd")))

    val mongoConf = Configuration((configuration getConfig "yunkai.mongo").get.underlying atPath "mongo.yunkai")
    val serviceConf = (configuration getConfig "services").get
    val injector = basicInjector.createChildInjector(new MorphiaModule(mongoConf, serviceConf), new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[FuturePool]) toInstance FuturePool.unboundedPool
      }
    })

    (configuration, injector)
  }

  val conf = configuration.underlying
}
