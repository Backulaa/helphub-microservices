package com.helphub.gateway.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;

import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MdcContextPropagationConfig {

    private static final String MDC_KEY = "requestId";

    @PostConstruct
    public void registerMdcAccessor() {

        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(
                        MDC_KEY,
                        () -> MDC.get(MDC_KEY),
                        value -> {
                            if (value == null) {
                                MDC.remove(MDC_KEY);
                            } else {
                                MDC.put(MDC_KEY, value);
                            }
                        },
                        () -> MDC.remove(MDC_KEY));
    }
}
