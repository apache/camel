package org.apache.camel.component.springrabbit;

public final class RabbitMQHelper {

    private RabbitMQHelper() {
    }

    public static boolean isDefaultExchange(String exchangeName) {
        return exchangeName == null || exchangeName.isEmpty()
                || exchangeName.equalsIgnoreCase(RabbitMQConstants.DEFAULT_EXCHANGE_NAME);
    }
}
