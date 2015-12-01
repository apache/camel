package org.apache.camel.component.kinesis;

import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.UUID;

import static java.lang.String.format;
import static org.apache.camel.component.kinesis.KinesisConstants.MAX_PRODUCER_CONNECTIONS;
import static org.apache.camel.component.kinesis.KinesisConstants.RECORD_BUFFERED_TIME;
import static org.apache.camel.component.kinesis.KinesisConstants.REQUEST_TIMEOUT;

/**
 * Created by alina on 02.11.15.
 */
public class KinesisProducer<K, V> extends DefaultProducer {
    private final Logger logger = LoggerFactory.getLogger(KinesisProducer.class);
    private com.amazonaws.services.kinesis.producer.KinesisProducer kinesisProducer;
    private final KinesisEndpoint kinesisEndpoint;

    public KinesisProducer(KinesisEndpoint endpoint) {
        super(endpoint);
        this.kinesisEndpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        KinesisProducerConfiguration config = new KinesisProducerConfiguration();
        config.setRegion(kinesisEndpoint.getRegion());
        config.setCredentialsProvider(CredentialsProvider.getAwsSessionCredentialsProvider());
        config.setMaxConnections(MAX_PRODUCER_CONNECTIONS);
        config.setRequestTimeout(REQUEST_TIMEOUT);
        config.setRecordMaxBufferedTime(RECORD_BUFFERED_TIME);
        kinesisProducer = new com.amazonaws.services.kinesis.producer.KinesisProducer(config);
    }


    @Override
    public void process(Exchange exchange) throws Exception {
        String partitionKey = (String) exchange.getIn().getHeader(KinesisConstants.PARTITION_KEY);
        partitionKey = (StringUtils.isEmpty(partitionKey)) ? kinesisEndpoint.getPartitionKey() : partitionKey;
        partitionKey = (StringUtils.isEmpty(partitionKey)) ? UUID.randomUUID().toString() : partitionKey;

        getCallbackOfSendingMessage(
                kinesisProducer.addUserRecord(
                        kinesisEndpoint.getStreamName(),
                        partitionKey,
                        exchange.getIn().getBody(ByteBuffer.class))
        );
    }

    private FutureCallback<UserRecordResult> getCallbackOfSendingMessage(ListenableFuture<UserRecordResult> sendMessageResult) {
        FutureCallback<UserRecordResult> kinesisDataCallback =
                new FutureCallback<UserRecordResult>() {
                    @Override
                    public void onFailure(Throwable t) {
                        logger.error(format("Unable to send message to streamName. %s", kinesisEndpoint.getStreamName()), t);
                    };

                    @Override
                    public void onSuccess(UserRecordResult result) {
                        logger.info("Message was sent to streamName " +
                                kinesisEndpoint.getStreamName() + " with partitionKey: "
                                + kinesisEndpoint.getPartitionKey() + " and region: " + kinesisEndpoint.getRegion());
                    };
                };
        Futures.addCallback(sendMessageResult, kinesisDataCallback);
        return kinesisDataCallback;
    }
}
