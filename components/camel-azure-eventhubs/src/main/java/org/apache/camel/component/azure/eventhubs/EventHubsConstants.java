package org.apache.camel.component.azure.eventhubs;

public class EventHubsConstants {
    private static final String HEADER_PREFIX = "CamelAzureEventHubs";

    public static final String PARTITION_KEY = HEADER_PREFIX + "PartitionKey";
    public static final String PARTITION_ID = HEADER_PREFIX + "PartitionKId";
}
