package org.apache.camel.component.kinesis;

import com.amazonaws.services.kinesis.model.Record;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static java.lang.String.format;
import static org.apache.camel.component.kinesis.KinesisConstants.CONSUMER_WORKER_NUMBER;

/**
 * Created by alina on 02.11.15.
 */
public class KinesisEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    private final Logger logger = LoggerFactory.getLogger(KinesisEndpoint.class);
    private String region;
    private String streamName;
    private String partitionKey;
    private String applicationName;

    public KinesisEndpoint() {
    }


    public KinesisEndpoint(String endpointUri,
                           String remaining,
                           KinesisComponent component) {
        super(endpointUri, component);
        logger.info(format("Started kinesis endpoint on url: %s", remaining));
    }

    public Exchange createKinesisExchange(Record record) {
        Exchange exchange = new DefaultExchange(getCamelContext(), getExchangePattern());
        Message message = new DefaultMessage();
        message.setHeader(KinesisConstants.PARTITION_KEY, record.getPartitionKey());
        message.setBody(record.getData().array());
        exchange.setIn(message);
        return exchange;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KinesisProducer<>(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new KinesisConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public ThreadPoolExecutor createExecutor() {
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(CONSUMER_WORKER_NUMBER);
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
