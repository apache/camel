package org.apache.camel.component.azure.storage.queue;

public final class QueueConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageQueue";
    // header names
    public static final String QUEUE_OPERATION = HEADER_PREFIX + "Operation";
    public static final String QUEUE_HTTP_HEADERS = HEADER_PREFIX + "HttpHeaders";
    // headers to be retrieved
    public static final String QUEUES_SEGMENT_OPTIONS = HEADER_PREFIX + "QueuesSegmentOptions";
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
}
