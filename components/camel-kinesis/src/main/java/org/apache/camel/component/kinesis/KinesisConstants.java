package org.apache.camel.component.kinesis;

/**
 * Created by alina on 02.11.15.
 */
public abstract class KinesisConstants {
    public static final String PARTITION_KEY = "kinesis.partiotionKey";
    public static final int NUM_RETRIES = 10;
    public static final int BACKOFF_TIME_IN_MILLIS = 3000;
    public static final int MAX_PRODUCER_CONNECTIONS = 1;
    public static final int CONSUMER_WORKER_NUMBER = 1;
    public static final int REQUEST_TIMEOUT = 60000;
    public static final int RECORD_BUFFERED_TIME = 15000;

    public KinesisConstants() {}
}
