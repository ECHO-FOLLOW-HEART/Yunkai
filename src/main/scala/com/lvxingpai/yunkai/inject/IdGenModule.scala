package com.lvxingpai.yunkai.inject

import com.google.inject.AbstractModule
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.idgen.IdGen

/**
 * Created by zephyre on 11/21/15.
 */
class IdGenModule(config: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[IdGen.FinagledClient]) toProvider new IdGenProvider(config)
  }
}
