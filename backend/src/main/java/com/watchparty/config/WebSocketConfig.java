package com.watchparty.config;

import com.watchparty.websocket.WatchPartyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Registers the raw WebSocket endpoint at /ws.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WatchPartyHandler handler;
    private final AppProperties props;

    public WebSocketConfig(WatchPartyHandler handler, AppProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .setAllowedOriginPatterns(props.allowedOriginArray());
    }

    /** Container limits: small text frames only, and drop connections that go silent. */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(16 * 1024);
        container.setMaxBinaryMessageBufferSize(1024);
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }
}
