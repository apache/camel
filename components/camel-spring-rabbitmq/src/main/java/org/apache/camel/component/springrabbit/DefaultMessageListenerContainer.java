package org.apache.camel.component.springrabbit;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;

public class DefaultMessageListenerContainer extends DirectMessageListenerContainer {

    private AmqpAdmin amqpAdmin;

    public DefaultMessageListenerContainer(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    @Override
    public void setAmqpAdmin(AmqpAdmin amqpAdmin) {
        super.setAmqpAdmin(amqpAdmin);
        this.amqpAdmin = amqpAdmin;
    }

    public AmqpAdmin getAmqpAdmin() {
        return amqpAdmin;
    }
}
