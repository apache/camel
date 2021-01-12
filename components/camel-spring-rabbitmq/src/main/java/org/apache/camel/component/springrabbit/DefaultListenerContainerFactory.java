package org.apache.camel.component.springrabbit;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

public class DefaultListenerContainerFactory implements ListenerContainerFactory {

    @Override
    public AbstractMessageListenerContainer createListenerContainer(RabbitMQEndpoint endpoint) {
        DefaultMessageListenerContainer listener = new DefaultMessageListenerContainer(endpoint.getConnectionFactory());
        if (endpoint.getQueues() != null) {
            listener.setQueueNames(endpoint.getQueues().split(","));
        }

        AmqpAdmin admin = endpoint.getComponent().getAmqpAdmin();
        if (endpoint.isAutoDeclare() && admin == null) {
            admin = new RabbitAdmin(endpoint.getConnectionFactory());
        }
        listener.setAutoDeclare(endpoint.isAutoDeclare());
        listener.setAmqpAdmin(admin);
        if (endpoint.getComponent().getErrorHandler() != null) {
            listener.setErrorHandler(endpoint.getComponent().getErrorHandler());
        }
        listener.setPrefetchCount(endpoint.getComponent().getPrefetchCount());
        listener.setShutdownTimeout(endpoint.getComponent().getShutdownTimeout());
        return listener;
    }
}
