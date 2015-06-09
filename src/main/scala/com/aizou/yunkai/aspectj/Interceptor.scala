package com.aizou.yunkai.aspectj

import java.lang.annotation.Annotation
import org.aspectj.weaver.tools.{PointcutParser, PointcutExpression}

/**
 * base trait for interceptors
 * defines val for pointcut and invoke method which can be overwritten by
 * interception traits
 * @author Christopher Schmidt
 */
trait Interceptor {
  // 横切点表达式解析器
  protected val parser = PointcutParser.getPointcutParserSupportingAllPrimitivesAndUsingContextClassloaderForResolution
  // 匹配预编译的横切点表达式
  protected def matches(pointcut: PointcutExpression, invocation: Invocation): Boolean = {
    pointcut.matchesMethodExecution(invocation.method).alwaysMatches ||
            invocation.target.getClass.getDeclaredMethods.exists(pointcut.matchesMethodExecution(_).alwaysMatches) ||
            false
  }
  // 用指定的注解来匹配
  protected def matches[T <: Annotation](annotationClass: Class[T], invocation: Invocation): Boolean = {
    invocation.method.isAnnotationPresent(annotationClass) ||
      invocation.target.getClass.isAnnotationPresent(annotationClass) ||
      false
  }
  // 调用
  def invoke(invocation: Invocation): AnyRef
  // 横切点表达式
  val pointcut: PointcutExpression
}

/**
 * 拦截器调用程序
 */
abstract trait InterceptorInvoker extends Interceptor {
  // advice
  def before: AnyRef = null
  def after(result: AnyRef): AnyRef = result
  def around(invoke: => AnyRef): AnyRef = invoke

  abstract override def invoke(invocation: Invocation): AnyRef =
    if (matches(pointcut, invocation)) {
      before
      val result = around(super.invoke(invocation))
      after(result)
    } else
      super.invoke(invocation)
}

/**
 *
 */
trait BeforeInterceptor extends InterceptorInvoker {
  def before: AnyRef
}

/**
 *
 */
trait AfterInterceptor extends InterceptorInvoker {
  def after(result: AnyRef): AnyRef
}

/**
 *
 */
trait AroundInterceptor extends InterceptorInvoker {
  def around(invoke: => AnyRef): AnyRef
}
