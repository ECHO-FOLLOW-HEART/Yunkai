package com.lvxingpai.yunkai.inject

import com.google.inject.AbstractModule
import com.lvxingpai.configuration.Configuration
import com.lvxingpai.smscenter.SmsCenter

/**
 * Created by zephyre on 11/21/15.
 */
class SmsCenterModule(config: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[SmsCenter.FinagledClient]) toProvider new SmsCenterProvider(config)
  }
}
