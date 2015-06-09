package com.aizou.yunkai.aspectj.test;

import org.aspectj.lang.annotation.Pointcut;

/**
 * User: FaKod
 * Date: 15.07.2010
 * Time: 12:23:22
 */
public class AspectBaseForConditionalPointcut {

    /**
     * creating public static boolean ... method
     * required for conditional pointcuts
     */
    @Pointcut("execution(* *.lolli(String)) && args(s)")
    public static boolean conditionalPointcut(String s) {//, AspectBaseForConditionalPointcut aa) {
        return true;
    }
}
