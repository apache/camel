package org.apache.camel.component.kinesis.utils;

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.apache.camel.component.kinesis.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static org.apache.camel.component.kinesis.KinesisConstants.MAX_PRODUCER_CONNECTIONS;
import static org.apache.camel.component.kinesis.KinesisConstants.RECORD_BUFFERED_TIME;
import static org.apache.camel.component.kinesis.KinesisConstants.REQUEST_TIMEOUT;

/**
 * Created by alina on 30.10.15.
 */
public abstract class CommonKinesisProducer<T> {
    private static final Logger log = LoggerFactory.getLogger(CommonKinesisProducer.class);
    private KinesisProducer kinesis;

    public KinesisProducer getKinesisProducer() {
        KinesisProducerConfiguration config = new KinesisProducerConfiguration();
        config.setRegion(getRegion());
        config.setCredentialsProvider(CredentialsProvider.getAwsSessionCredentialsProvider());
        config.setMaxConnections(MAX_PRODUCER_CONNECTIONS);
        config.setRequestTimeout(REQUEST_TIMEOUT);
        config.setRecordMaxBufferedTime(RECORD_BUFFERED_TIME);
        return new KinesisProducer(config);
    }

    public abstract String getRegion();

    public abstract String getStreamName();

    public abstract String getPartitionKey();

    public abstract ByteBuffer sendMessage(T message);

    public void start() {
        kinesis = getKinesisProducer();
    }

    public void destroy() {
        kinesis.destroy();
    }

    public void execute(T message) {
        Futures.addCallback(
                kinesis.addUserRecord(getStreamName(), getPartitionKey(), sendMessage(message)),
                new FutureCallback<UserRecordResult>() {
                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Cannot receive message: ", t);
                    };

                    @Override
                    public void onSuccess(UserRecordResult result) {
                        log.info(String.format("Message was successful sent on shard id: %s", result.getShardId()));
                    };
                });
    }
}
