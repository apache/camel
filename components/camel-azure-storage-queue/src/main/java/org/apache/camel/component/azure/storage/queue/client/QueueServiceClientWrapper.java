package org.apache.camel.component.azure.storage.queue.client;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueItem;
import com.azure.storage.queue.models.QueuesSegmentOptions;
import org.apache.camel.util.ObjectHelper;

public class QueueServiceClientWrapper {

    private final QueueServiceClient client;

    public QueueServiceClientWrapper(final QueueServiceClient client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
    }

    public List<QueueItem> listQueues(QueuesSegmentOptions options, Duration timeout) {
        return client.listQueues(options, timeout, Context.NONE).stream().collect(Collectors.toList());
    }
}
