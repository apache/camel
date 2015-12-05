package org.apache.camel.component.kinesis.utils;


import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import org.apache.camel.component.kinesis.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.apache.camel.component.kinesis.KinesisConstants.CONSUMER_WORKER_NUMBER;

/**
 * Created by alina on 30.10.15.
 */
public abstract class CommonKinesisConsumer implements IRecordProcessorFactory {
    private static final Logger log = LoggerFactory.getLogger(CommonKinesisConsumer.class);

    public abstract String getApplicationName();

    public abstract String getRegion();

    public abstract String getStreamName();

    public abstract IRecordProcessorFactory getRecordProcessorFactory();

    public KinesisClientLibConfiguration getKinesisClientLibConfiguration() throws UnknownHostException {
        return new KinesisClientLibConfiguration(
                getApplicationName(),
                getStreamName(),
                CredentialsProvider.getAwsSessionCredentialsProvider(),
                InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID())
                .withRegionName(getRegion())
                .withInitialPositionInStream(InitialPositionInStream.LATEST);
    }

    public void execute() {
        try {
            Executors.newFixedThreadPool(CONSUMER_WORKER_NUMBER)
                    .submit(new Worker.Builder()
                            .recordProcessorFactory(getRecordProcessorFactory())
                            .config(getKinesisClientLibConfiguration())
                            .build());
        } catch (Exception e) {
            log.error("Cannot start consumer worker: ", e);
        }

    }
}
