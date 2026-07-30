package org.example.serverconnectivity;
import com.rabbitmq.jms.admin.RMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import jakarta.jms.ConnectionFactory;

@Configuration // yoo spring boot this class has some config setting and definitions
               //for spring beans to be instantiated during app setup
@EnableJms     // turns on Spring's background message-listening machiner
public class JmsBrokerConfig {
    // @Value : inject settings from your application.properties or application.yml file.
    // can have default values if the attributs are not set
    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:hornet}")
    private String username;

    @Value("${spring.rabbitmq.password:hornet}")
    private String password;

    // 1. Create a standard JMS ConnectionFactory implemented by RabbitMQ driver
    //Registers a bean returning the standard jakarta.jms.ConnectionFactory interface.
    // can swap up rabbitMQ later without a probleme
    @Bean
    public ConnectionFactory connectionFactory() {
        //Instantiates RabbitMQ’s JMS driver
        RMQConnectionFactory factory = new RMQConnectionFactory();
        // add network properties + user + pwd
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    // 2. Configure standard JMS Listener Container for consuming messages
    //Creates the factory responsible for managing background listener
    // threads that process incoming messages
    // for example, handling completed task results from agents
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        // Creates the container factory and binds it to the RabbitMQ ConnectionFactory
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // Enable JSON/Text deserialization & concurrency settings
        factory.setConcurrency("3-10");
        //means Spring will start with 3 active listener threads
        // to handle messages concurrently, and automatically
        // scale up to 10 threads under heavy message load.
        return factory;
    }

}
