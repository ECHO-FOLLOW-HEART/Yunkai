package com.aizou.yunkai.aspectj

import java.lang.reflect.{Proxy, InvocationHandler, Method}

import scala.reflect.ClassTag

/**
 * User: FaKod
 * Date: 13.07.2010
 * Time: 15:49:37
 */
// 切面, 参数是一个横切点表达式, 继承了特质Interceptor
class Aspect(val pointcutExpression: String) extends Interceptor {
  def create[I: ClassTag](target: AnyRef): I = {

    val interface = implicitly[ClassTag[I]]

    val proxy = new TargetProxy(this, target)

    Proxy.newProxyInstance(
      target.getClass.getClassLoader,
      Array(interface.runtimeClass),
      proxy).asInstanceOf[I]
  }

  def invoke(invocation: Invocation): AnyRef = invocation.invoke
  // 将横切点表达式解析成横切点
  val pointcut = parser.parsePointcutExpression(pointcutExpression)
}


/**
 *
 */
class TargetProxy(val aspect: Aspect, val target: AnyRef) extends InvocationHandler {
  def invoke(proxy: AnyRef, m: Method, args: Array[AnyRef]): AnyRef = aspect.invoke(Invocation(m, args, target))
}

//// 这个用的是伴生对象, proxy通过参数传递进来, 更加的优雅
//object ManagedComponentFactory {
//  def createComponent[T](intf: Class[T] forSome {type T}, proxy: ManagedComponentProxy): T =
//    Proxy.newProxyInstance(
//      proxy.target.getClass.getClassLoader,
//      Array(intf),
//      proxy).asInstanceOf[T]
//}
//
//class ManagedComponentProxy(val target: AnyRef) extends InvocationHandler {
//  def invoke(proxy: AnyRef, m: Method, args: Array[AnyRef]): AnyRef = invoke(Invocation(m, args, target))
//  def invoke(invocation: Invocation): AnyRef = invocation.invoke
//}

