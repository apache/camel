package org.apache.camel.component.azure.storage.queue.operations;

import java.util.Properties;
import java.util.stream.Collectors;

import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueItem;
import com.azure.storage.queue.models.QueueMessageItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.azure.storage.queue.QueueTestUtils;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueOperationsIT extends CamelTestSupport {

    private QueueConfiguration configuration;
    private QueueServiceClientWrapper serviceClientWrapper;

    @BeforeAll
    public void setup() throws Exception {
        final Properties properties = QueueTestUtils.loadAzureAccessFromJvmEnv();

        configuration = new QueueConfiguration();
        configuration.setAccountName(properties.getProperty("account_name"));
        configuration.setAccessKey(properties.getProperty("access_key"));

        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        serviceClientWrapper = new QueueServiceClientWrapper(client);
    }

    @Test
    public void testCreateDeleteQueue() {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final QueueClientWrapper clientWrapper = serviceClientWrapper.getQueueClientWrapper(queueName);
        final QueueOperations operations = new QueueOperations(configuration, clientWrapper);

        // test create queue
        final QueueOperationResponse response = operations.createQueue(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders());
        assertTrue((boolean) response.getBody());
        assertTrue(serviceClientWrapper.listQueues(null, null)
                .stream()
                .map(QueueItem::getName)
                .collect(Collectors.toList())
                .contains(queueName));

        // delete and test
        final QueueOperationResponse response2 = operations.deleteQueue(null);
        assertNotNull(response2);
        assertNotNull(response2.getHeaders());
        assertTrue((boolean) response2.getBody());
        assertFalse(serviceClientWrapper.listQueues(null, null)
                .stream()
                .map(QueueItem::getName)
                .collect(Collectors.toList())
                .contains(queueName));
    }

    @Test
    public void testSendMessageAndClearQueue() {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final QueueClientWrapper clientWrapper = serviceClientWrapper.getQueueClientWrapper(queueName);
        final QueueOperations operations = new QueueOperations(configuration, clientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(QueueConstants.MESSAGE_TEXT, "testing message");

        final QueueOperationResponse response = operations.sendMessage(exchange);

        assertNotNull(response);
        assertNotNull(response.getHeaders());
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders().get(QueueConstants.MESSAGE_ID));
        assertNotNull(response.getHeaders().get(QueueConstants.EXPIRATION_TIME));

        final QueueMessageItem messageItem = clientWrapper.receiveMessages(1, null, null).stream().findFirst().get();

        assertEquals("testing message", messageItem.getMessageText());

        // test clear queue
        operations.clearQueue(exchange);

        assertTrue(clientWrapper.receiveMessages(1, null, null).isEmpty());

        // delete testing queue
        operations.deleteQueue(exchange);
    }
}