package org.apache.camel.component.azure.servicebus.integration.operations;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Spliterator;
import java.util.stream.StreamSupport;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.component.azure.servicebus.TestUtils;
import org.apache.camel.component.azure.servicebus.client.ServiceBusReceiverAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.operations.ServiceBusProducerOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceBusProducerOperationsIT {

    private ServiceBusSenderAsyncClientWrapper clientSenderWrapper;
    private ServiceBusReceiverAsyncClientWrapper clientReceiverWrapper;

    @BeforeAll
    void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        final ServiceBusSenderAsyncClient senderClient = new ServiceBusClientBuilder()
                .connectionString(properties.getProperty(TestUtils.CONNECTION_STRING))
                .sender()
                .buildAsyncClient();

        clientSenderWrapper = new ServiceBusSenderAsyncClientWrapper(senderClient);
    }

    @BeforeEach
    void prepareReceiver() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        final ServiceBusReceiverAsyncClient receiverClient = new ServiceBusClientBuilder()
                .connectionString(properties.getProperty(TestUtils.CONNECTION_STRING))
                .receiver()
                .topicName(properties.getProperty(TestUtils.TOPIC_NAME))
                .subscriptionName(properties.getProperty(TestUtils.SUBSCRIPTION_NAME))
                .buildAsyncClient();

        clientReceiverWrapper = new ServiceBusReceiverAsyncClientWrapper(receiverClient);
    }

    @AfterAll
    void closeClient() {
        clientSenderWrapper.close();
    }

    @AfterEach
    void closeSubscriber() {
        clientReceiverWrapper.close();
    }

    @Test
    void testSendSingleMessage() {
        final ServiceBusProducerOperations operations = new ServiceBusProducerOperations(clientSenderWrapper);

        operations.sendMessages("test data", null).block();

        final boolean exists = StreamSupport.stream(clientReceiverWrapper.receiveMessages().toIterable().spliterator(), false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test data"));

        assertTrue(exists, "test message body");

        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.sendMessages(12345, null).block();
        });
    }

    @Test
    void testSendingBatchMessages() {
        final ServiceBusProducerOperations operations = new ServiceBusProducerOperations(clientSenderWrapper);

        final List<String> inputBatch = new LinkedList<>();
        inputBatch.add("test batch 1");
        inputBatch.add("test batch 2");
        inputBatch.add("test batch 3");

        operations.sendMessages(inputBatch, null).block();

        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = clientReceiverWrapper.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 1"));

        final boolean batch2Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 2"));

        final boolean batch3Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 3"));

        assertTrue(batch1Exists, "test message body 1");
        assertTrue(batch2Exists, "test message body 2");
        assertTrue(batch3Exists, "test message body 3");
    }

    @Test
    void testScheduleMessage() {
        final ServiceBusProducerOperations operations = new ServiceBusProducerOperations(clientSenderWrapper);

        operations.scheduleMessages("testScheduleMessage", OffsetDateTime.now(), null).block();

        final boolean exists = StreamSupport.stream(clientReceiverWrapper.receiveMessages().toIterable().spliterator(), false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testScheduleMessage"));

        assertTrue(exists, "test message body");

        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.scheduleMessages(12345, OffsetDateTime.now(), null).block();
        });
    }

    @Test
    void testSchedulingBatchMessages() {
        final ServiceBusProducerOperations operations = new ServiceBusProducerOperations(clientSenderWrapper);

        final List<String> inputBatch = new LinkedList<>();
        inputBatch.add("testSchedulingBatchMessages 1");
        inputBatch.add("testSchedulingBatchMessages 2");
        inputBatch.add("testSchedulingBatchMessages 3");

        operations.scheduleMessages(inputBatch, OffsetDateTime.now(), null).block();

        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = clientReceiverWrapper.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testSchedulingBatchMessages 1"));

        final boolean batch2Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testSchedulingBatchMessages 2"));

        final boolean batch3Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testSchedulingBatchMessages 3"));

        assertTrue(batch1Exists, "test message body 1");
        assertTrue(batch2Exists, "test message body 2");
        assertTrue(batch3Exists, "test message body 3");
    }
}
