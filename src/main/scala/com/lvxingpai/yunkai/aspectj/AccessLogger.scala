package com.lvxingpai.yunkai.aspectj

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.{Aspect, Before}
import org.slf4j.LoggerFactory


/**
 * Created by zephyre on 7/6/15.
 */
@Aspect
class AccessLogger {
  @Before("execution(com.twitter.util.Future com.lvxingpai.yunkai.handler.UserServiceHandler..*(..))")
  def log(jp: JoinPoint): Unit = {
    val signature = jp.getSignature
    val message = s"Invoked: ${signature.toLongString}"

    val logger = LoggerFactory.getLogger("access")
    logger.info(message)
  }
}
