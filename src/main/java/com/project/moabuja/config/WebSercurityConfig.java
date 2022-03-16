package com.project.moabuja.config;

import com.project.moabuja.security.filter.CustomAuthenticationFilter;
import com.project.moabuja.security.filter.JwtExceptionFilter;
import com.project.moabuja.security.filter.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

@Configuration
@RequiredArgsConstructor
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSercurityConfig extends WebSecurityConfigurerAdapter {

    private final JwtTokenProvider jwtProvider;
    private final RedisTemplate redisTemplate;
    private final CorsFilter corsFilter;


    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/h2-console/**")
                .antMatchers("/api/reissue"); }


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.headers().frameOptions().sameOrigin();
        http.csrf().disable();
//        http.csrf().ignoringAntMatchers("/h2-console/**").disable();
        http.addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class);
        http.httpBasic().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
//        http.requiresChannel()
//                .antMatchers("/h2-console/**").requiresInsecure();

        http
                .authorizeRequests()
                .antMatchers("/index.html").permitAll()
                .antMatchers(HttpMethod.GET, "/user/kakao/callback").permitAll()
                .antMatchers(HttpMethod.GET, "/api/reissue").permitAll()
                .antMatchers(HttpMethod.GET, "/health").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                // h2 콘솔 추가
                .anyRequest().authenticated();

        http    .addFilterBefore(new CustomAuthenticationFilter(jwtProvider, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        // todo : exception을 핸들링 하기 위한 필터를 설치
        http    .addFilterBefore(new JwtExceptionFilter(), CustomAuthenticationFilter.class);
    }
}
