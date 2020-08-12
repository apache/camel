package org.apache.camel.component.azure.eventhubs;

public class EventHubsConstants {
    private static final String HEADER_PREFIX = "CamelAzureEventHubs";
    // common headers, set by consumer and evaluated by producer
    public static final String PARTITION_KEY = HEADER_PREFIX + "PartitionKey";
    public static final String PARTITION_ID = HEADER_PREFIX + "PartitionId";
    // headers set by the consumer
    public static final String OFFSET = HEADER_PREFIX + "Offset";
    public static final String ENQUEUED_TIME = HEADER_PREFIX + "EnqueuedTime";
    public static final String SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";
}
