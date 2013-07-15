package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * @author Stephen Samuel
 */
public class RabbitMQConsumer extends DefaultConsumer {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumer.class);

    private final RabbitMQEndpoint endpoint;

    ExecutorService executor;
    Connection conn;
    Channel channel;

    public RabbitMQConsumer(RabbitMQEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Starting RabbitMQ consumer");

        executor = endpoint.createExecutor();
        logger.debug("Using executor {}", executor);

        conn = endpoint.connect(executor);
        logger.debug("Using conn {}", conn);

        channel = conn.createChannel();
        logger.debug("Using channel {}", channel);

        channel.exchangeDeclare(endpoint.getExchangeName(), "direct", true);
        channel.queueDeclare(endpoint.getQueue(), true, false, false, null);
        channel.queueBind(endpoint.getQueue(), endpoint.getExchangeName(),
                endpoint.getRoutingKey() == null ? "" : endpoint.getRoutingKey());

        channel.basicConsume(endpoint.getQueue(), endpoint.isAutoAck(), new RabbitConsumer(this, channel));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Stopping RabbitMQ consumer");
        if (conn != null)
            try {
                conn.close();
            } catch (Exception ignored) { }

        channel = null;
        conn = null;
        executor.shutdown();
        executor = null;
    }

    class RabbitConsumer extends com.rabbitmq.client.DefaultConsumer {

        private final RabbitMQConsumer consumer;
        private final Channel channel;

        /**
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        public RabbitConsumer(RabbitMQConsumer consumer, Channel channel) {
            super(channel);
            this.consumer = consumer;
            this.channel = channel;
        }

        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body)
                throws IOException {

            Exchange exchange = consumer.endpoint.createRabbitExchange(envelope);
            logger.trace("Created exchange [exchange={}]", new Object[]{exchange});

            try {
                consumer.getProcessor().process(exchange);

                long deliveryTag = envelope.getDeliveryTag();
                logger.trace("Acknowleding receipt [delivery_tag={}]", deliveryTag);
                channel.basicAck(deliveryTag, false);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

