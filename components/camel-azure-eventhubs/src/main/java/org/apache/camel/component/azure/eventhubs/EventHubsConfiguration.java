package org.apache.camel.component.azure.eventhubs;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpTransportType;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class EventHubsConfiguration implements Cloneable {

    @UriPath
    private String namespace;
    @UriPath
    private String eventHubName;
    @UriParam(label = "security")
    private String sharedAccessName;
    @UriParam(label = "security", secret = true)
    private String sharedAccessKey;
    @UriParam(label = "security", secret = true)
    private String connectionString;
    @UriParam(label = "common")
    private AmqpTransportType amqpTransportType;
    @UriParam(label = "common")
    private AmqpRetryOptions amqpRetryOptions;
    @UriParam(label = "common", defaultValue = "true")
    private boolean autoDiscoverClient = true;
    @UriParam(label = "consumer")
    private EventHubConsumerAsyncClient consumerAsyncClient;
    @UriParam(label = "consumer")
    private String consumerGroupName;
    @UriParam(label = "consumer", defaultValue = "500")
    private int prefetchCount = 500;
    @UriParam(label = "producer")
    private EventHubProducerAsyncClient producerAsyncClient;

    /**
     * test
     */
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * test
     */
    public String getEventHubName() {
        return eventHubName;
    }

    public void setEventHubName(String eventHubName) {
        this.eventHubName = eventHubName;
    }

    /**
     * test
     */
    public String getSharedAccessName() {
        return sharedAccessName;
    }

    public void setSharedAccessName(String sharedAccessName) {
        this.sharedAccessName = sharedAccessName;
    }

    /**
     * test
     */
    public String getSharedAccessKey() {
        return sharedAccessKey;
    }

    public void setSharedAccessKey(String sharedAccessKey) {
        this.sharedAccessKey = sharedAccessKey;
    }

    /**
     * test
     */
    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * test
     */
    public AmqpTransportType getAmqpTransportType() {
        return amqpTransportType;
    }

    public void setAmqpTransportType(AmqpTransportType amqpTransportType) {
        this.amqpTransportType = amqpTransportType;
    }

    /**
     * test
     */
    public AmqpRetryOptions getAmqpRetryOptions() {
        return amqpRetryOptions;
    }

    public void setAmqpRetryOptions(AmqpRetryOptions amqpRetryOptions) {
        this.amqpRetryOptions = amqpRetryOptions;
    }

    /**
     * test
     */
    public EventHubConsumerAsyncClient getConsumerAsyncClient() {
        return consumerAsyncClient;
    }

    public void setConsumerAsyncClient(EventHubConsumerAsyncClient consumerAsyncClient) {
        this.consumerAsyncClient = consumerAsyncClient;
    }

    /**
     * test
     */
    public String getConsumerGroupName() {
        return consumerGroupName;
    }

    public void setConsumerGroupName(String consumerGroupName) {
        this.consumerGroupName = consumerGroupName;
    }

    /**
     * test
     */
    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * test
     */
    public EventHubProducerAsyncClient getProducerAsyncClient() {
        return producerAsyncClient;
    }

    public void setProducerAsyncClient(EventHubProducerAsyncClient producerAsyncClient) {
        this.producerAsyncClient = producerAsyncClient;
    }

    /**
     * Setting the autoDiscoverClient mechanism, if true, the component will
     * look for a client instance in the registry automatically otherwise it
     * will skip that checking.
     */
    public boolean isAutoDiscoverClient() {
        return autoDiscoverClient;
    }

    public void setAutoDiscoverClient(boolean autoDiscoverClient) {
        this.autoDiscoverClient = autoDiscoverClient;
    }

    // *************************************************
    //
    // *************************************************

    public EventHubsConfiguration copy() {
        try {
            return (EventHubsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
