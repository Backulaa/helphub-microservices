package com.helphub.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "requestCorrelationFilterRegistration")
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration() {

        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new RequestCorrelationFilter());
        registration.addUrlPatterns("/*");
        registration.setName("requestCorrelationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}
