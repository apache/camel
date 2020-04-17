package org.apache.camel.component.azure.storage.queue;

public final class QueueConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageQueue";
    // header names
    public static final String QUEUE_OPERATION = HEADER_PREFIX + "Operation";
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    public static final String MESSAGE_ID = HEADER_PREFIX + "MessageId";
    public static final String INSERTION_TIME = HEADER_PREFIX + "InsertionTime";
    public static final String EXPIRATION_TIME = HEADER_PREFIX + "ExpirationTime";
    public static final String POP_RECEIPT = HEADER_PREFIX + "PopReceipt";
    public static final String TIME_NEXT_VISIBLE = HEADER_PREFIX + "TimeNextVisible";
    // headers to be retrieved
    public static final String QUEUES_SEGMENT_OPTIONS = HEADER_PREFIX + "QueuesSegmentOptions";
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    public static final String MESSAGE_TEXT = HEADER_PREFIX + "MessageText";
    public static final String MAX_MESSAGES = HEADER_PREFIX + "MaxMessages";
    public static final String VISIBILITY_TIMEOUT = HEADER_PREFIX + "VisibilityTimeout";
    public static final String TIME_TO_LIVE = HEADER_PREFIX + "TimeToLive";
    public static final String QUEUE_CREATED = HEADER_PREFIX + "QueueCreated";

}
