package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Stephen Samuel
 */
public class RabbitMQEndpoint extends DefaultEndpoint {

    private String username;
    private String password;
    private String vhost;
    private String hostname;
    private int threadPoolSize = 10;
    private int portNumber;
    private boolean autoAck = true;
    private String queue = String.valueOf(UUID.randomUUID().toString().hashCode());
    private String exchangeName;
    private String routingKey;

    public String getExchangeName() {
        return exchangeName;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public String getQueue() {
        return queue;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public RabbitMQEndpoint() {
    }

    public RabbitMQEndpoint(String endpointUri,
                            String remaining,
                            RabbitMQComponent component) throws URISyntaxException {
        super(endpointUri, component);

        URI uri = new URI("http://" + remaining);
        hostname = uri.getHost();
        portNumber = uri.getPort();
        exchangeName = uri.getPath().substring(1);
    }

    public Exchange createRabbitExchange(Envelope envelope) {
        Exchange exchange = new DefaultExchange(getCamelContext(), getExchangePattern());

        Message message = new DefaultMessage();
        exchange.setIn(message);

        message.setHeader(RabbitMQConstants.ROUTING_KEY, envelope.getRoutingKey());
        message.setHeader(RabbitMQConstants.EXCHANGE_NAME, envelope.getExchange());
        message.setHeader(RabbitMQConstants.DELIVERY_TAG, envelope.getDeliveryTag());

        return exchange;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Connection connect(ExecutorService executor) throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(getUsername());
        factory.setPassword(getPassword());
        if (getVhost() == null)
            factory.setVirtualHost("/");
        else
            factory.setVirtualHost(getVhost());
        factory.setHost(getHostname());
        factory.setPort(getPortNumber());
        return factory.newConnection(executor);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new RabbitMQProducer(this);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public String getHostname() {
        return hostname;
    }

    public String getVhost() {
        return vhost;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public ThreadPoolExecutor createExecutor() {
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(getThreadPoolSize());
    }
}
