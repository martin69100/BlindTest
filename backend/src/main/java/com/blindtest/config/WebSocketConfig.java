package com.blindtest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Préfixes des topics de diffusion (publics et privés)
        config.enableSimpleBroker("/topic", "/queue");
        // Préfixe pour les messages entrants envoyés par les clients (@MessageMapping)
        config.setApplicationDestinationPrefixes("/app");
        // Préfixe pour les notifications ciblées par utilisateur
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint avec support SockJS (fallback navigateurs)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Endpoint WebSocket natif direct
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
