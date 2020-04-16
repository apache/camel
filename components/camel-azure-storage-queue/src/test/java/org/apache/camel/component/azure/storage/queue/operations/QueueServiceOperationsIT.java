package org.apache.camel.component.azure.storage.queue.operations;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueItem;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueTestUtils;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueServiceOperationsIT {

    private QueueConfiguration configuration;
    private QueueServiceClientWrapper clientWrapper;

    private String queueName1;
    private String queueName2;
    private String queueName3;

    @BeforeAll
    public void setup() throws Exception {
        final Properties properties = QueueTestUtils.loadAzureAccessFromJvmEnv();

        queueName1 = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        queueName2 = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        queueName3 = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        configuration = new QueueConfiguration();
        configuration.setAccountName(properties.getProperty("account_name"));
        configuration.setAccessKey(properties.getProperty("access_key"));

        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        clientWrapper = new QueueServiceClientWrapper(client);

        // create test queues
        client.createQueue(queueName1);
        client.createQueue(queueName2);
        client.createQueue(queueName3);
    }

    @Test
    public void testListQueues() {
        final QueueServiceOperations operations = new QueueServiceOperations(clientWrapper);

        // test
        final QueueOperationResponse queuesResponse = operations.listQueues(null);

        assertNotNull(queuesResponse);
        assertNotNull(queuesResponse.getBody());

        @SuppressWarnings("unchecked") final List<String> queues = ((List<QueueItem>) queuesResponse.getBody())
                .stream()
                .map(QueueItem::getName)
                .collect(Collectors.toList());

        assertTrue(queues.contains(queueName1));
        assertTrue(queues.contains(queueName2));
        assertTrue(queues.contains(queueName3));
    }

    @AfterAll
    public void tearDown() {
        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        client.deleteQueue(queueName1);
        client.deleteQueue(queueName2);
        client.deleteQueue(queueName3);
    }
}