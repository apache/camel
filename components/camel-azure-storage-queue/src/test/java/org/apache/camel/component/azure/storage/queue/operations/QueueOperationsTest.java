package org.apache.camel.component.azure.storage.queue.operations;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.queue.models.UpdateMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class QueueOperationsTest extends CamelTestSupport {

    private QueueConfiguration configuration;

    @Mock
    private QueueClientWrapper client;

    @BeforeEach
    public void setup() {
        configuration = new QueueConfiguration();
        configuration.setAccountName("test");
    }

    @Test
    public void testDeleteMessage() {
        // mocking
        final HttpHeaders httpHeaders = new HttpHeaders().put("x-test-header", "123");
        when(client.deleteMessage(any(), any(), any())).thenReturn(new ResponseBase<>(null, 200, httpHeaders, null, null));

        final QueueOperations operations = new QueueOperations(configuration, client);
        final Exchange exchange = new DefaultExchange(context);

        // test if we have nothing set on exchange
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));

        exchange.getIn().removeHeader(QueueConstants.MESSAGE_ID);
        exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, "12");
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        final QueueOperationResponse response = operations.deleteMessage(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
    }

    @Test
    public void testUpdateMessage() {
        //mocking
        final HttpHeaders httpHeaders = new HttpHeaders().put("x-test-header", "123");
        final UpdateMessageResult result = new UpdateMessageResult("12", OffsetDateTime.now());
        when(client.updateMessage(any(), any(), any(), any(), any())).thenReturn((new ResponseBase<>(null, 200, httpHeaders, result, null)));

        final QueueOperations operations = new QueueOperations(configuration, client);
        final Exchange exchange = new DefaultExchange(context);

        // test if we have nothing set on exchange
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().removeHeader(QueueConstants.MESSAGE_ID);
        exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, "12");
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.VISIBILITY_TIMEOUT, Duration.ofMillis(10));

        final QueueOperationResponse response = operations.updateMessage(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders());
    }
}
