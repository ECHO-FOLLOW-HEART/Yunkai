package com.lvxingpai.yunkai.aspectj

import com.lvxingpai.yunkai.Global
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.{ Aspect, Before }
import org.slf4j.LoggerFactory

/**
 * Created by zephyre on 7/6/15.
 */
@Aspect
class AccessLogger {
  private val runlevel = Global.conf.getString("runlevel")

  @Before("execution(com.twitter.util.Future com.lvxingpai.yunkai.handler.UserServiceHandler..*(..))")
  def log(jp: JoinPoint): Unit = {
    // 对应test的情况，不再记录日志
    if (runlevel != "test") {
      val signature = jp.getSignature
      val message = s"Invoked: ${signature.toLongString}"

      val logger = LoggerFactory.getLogger("access")
      //      logger.info(message)
    }
  }
}
