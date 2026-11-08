package com.qts.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class Auths {

    /** 取当前登录用户信息（由 JwtAuthFilter 写入） */
    public static JwtAuthFilter.CurrentUser me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthFilter.CurrentUser cu) {
            return cu;
        }
        throw new BizException("未登录");
    }
}
