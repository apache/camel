package org.apache.camel.component.aws2.kinesis.consumer;

import org.apache.camel.component.aws2.kinesis.Kinesis2Endpoint;

import java.util.Objects;

import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

public class KinesisConnection {

    private static KinesisConnection instance;
    private KinesisClient kinesisClient = null;
    private KinesisAsyncClient kinesisAsyncClient = null;

    private KinesisConnection() {
    }

    public static synchronized KinesisConnection getInstance() {
        if (instance == null) {
            synchronized (KinesisConnection.class) {
                if (instance == null) {
                    instance = new KinesisConnection();
                }
            }
        }
        return instance;
    }

    public KinesisClient getClient(final Kinesis2Endpoint endpoint) {
        if (Objects.isNull(kinesisClient)) {
            kinesisClient = endpoint.getClient();
        }
        return kinesisClient;
    }

    public KinesisAsyncClient getAsyncClient(final Kinesis2Endpoint endpoint) {
        if (Objects.isNull(kinesisAsyncClient)) {
            kinesisAsyncClient = endpoint.getAsyncClient();
        }
        return kinesisAsyncClient;
    }

    public void setKinesisClient(KinesisClient kinesisClient) {
        this.kinesisClient = kinesisClient;
    }

    public void setKinesisAsyncClient(KinesisAsyncClient kinesisAsyncClient) {
        this.kinesisAsyncClient = kinesisAsyncClient;
    }
}
