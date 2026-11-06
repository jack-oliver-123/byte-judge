package com.jack.bytejudge.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfiguration {
    /**
     * The gateway never redirects with flash attributes. A no-op manager keeps DispatcherServlet from
     * calling {@code request.getSession(false)} on every proxied request: the gateway would otherwise
     * load a session owned by the user service and re-save it after the owner rotates or deletes it,
     * leaving a partial Redis hash that makes later requests with that id fail.
     */
    @Bean
    FlashMapManager flashMapManager() {
        return new FlashMapManager() {
            @Override
            public FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) {
                return null;
            }

            @Override
            public void saveOutputFlashMap(FlashMap flashMap, HttpServletRequest request,
                                           HttpServletResponse response) {
                // The gateway has no redirect flow, so there is nothing to persist.
            }
        };
    }
}
