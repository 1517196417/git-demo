package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshInterceptor()).addPathPatterns("/***").order(0);

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code",
                                    "/user/login",
                                    "/blog/hot",
                                    "/shop/**",
                                    "/shop-type/**",
                                    "/upload/**",
                                    "/voucher/**"

                ).order(1)    ;
    }
}
