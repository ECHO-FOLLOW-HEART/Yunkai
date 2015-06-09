package com.aizou.yunkai.aspectj

import java.lang.reflect.Method

/**
 * 调用上下文
 * @param method
 * @param args
 * @param target
 */
case class Invocation(val method: Method, val args: Array[AnyRef], val target: AnyRef) {
  def invoke: AnyRef = method.invoke(target, args: _*)

  override def toString: String = "Invocation [method: " + method.getName + ", args: " + args + ", target: " + target + "]"

  override def hashCode(): Int = method.hashCode
  //override def equals (that: Any): Boolean = {...}
}