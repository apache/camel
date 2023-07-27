package org.apache.camel.component.aws2.kinesis.consumer;

import org.apache.camel.component.aws2.kinesis.Kinesis2Endpoint;

import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import software.amazon.awssdk.services.kinesis.model.ListStreamsRequest;

public class KinesisHealthCheck extends TimerTask {
    private Kinesis2Endpoint endpoint;
    private KinesisConnection kinesisConnection;

    public KinesisHealthCheck(Kinesis2Endpoint endpoint,
                              KinesisConnection kinesisConnection) {
        this.endpoint = endpoint;
        this.kinesisConnection = kinesisConnection;
    }

    @Override
    public void run() {
        if (this.endpoint.getConfiguration().isAsyncClient()) {
            try {
                if (Objects.isNull(kinesisConnection.getAsyncClient(this.endpoint)) ||
                        kinesisConnection.getAsyncClient(this.endpoint)
                                .listStreams(ListStreamsRequest
                                        .builder()
                                        .build())
                                .get()
                                .streamNames()
                                .isEmpty()) {
                    kinesisConnection.setKinesisAsyncClient(endpoint.getAsyncClient());
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (Objects.isNull(kinesisConnection.getClient(this.endpoint)) ||
                    kinesisConnection.getClient(this.endpoint)
                            .listStreams(ListStreamsRequest
                                    .builder()
                                    .build())
                            .streamNames()
                            .isEmpty()) {
                kinesisConnection.setKinesisClient(endpoint.getClient());
            }
        }
    }

}
