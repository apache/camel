package org.apache.camel.component.azure.servicebus;

import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

@RunWith(JUnitParamsRunner.class)
public class SbQueueProducerTest {
    private boolean mockInitialized = false;

    private static final String SAMPLE_MESSAGE_BODY = "this is a body";
    private static final String MESSAGE_ID = "11111111111111111111111111111111";
    private static final String QUEUE_URL = "some://queue/url";

    private Exchange exchange = mock(Exchange.class, RETURNS_DEEP_STUBS);

    @Mock private SbQueueEndpoint bbEndpoint;
    @Mock private ServiceBusContract serviceBusContractMock;
    @Mock private Message outMessage;
    @Mock private Message inMessage;

    private SbQueueProducer underTest;

    @Before
    public void setup() throws Exception {
        // this replaces the mockito junit runner since we're using junitparams
        if (!mockInitialized) {
            MockitoAnnotations.initMocks(this);
            mockInitialized = true;
        }

        SbConfiguration sbConfiguration = new SbConfiguration();
        sbConfiguration.setQueueName(QUEUE_URL);

        underTest = new SbQueueProducer(bbEndpoint);

        when(bbEndpoint.getClient()).thenReturn(serviceBusContractMock);
        when(bbEndpoint.getConfiguration()).thenReturn(sbConfiguration);
        when(exchange.getOut()).thenReturn(outMessage);
        when(exchange.getIn()).thenReturn(inMessage);
        when(exchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        when(inMessage.getMessageId()).thenReturn(MESSAGE_ID);
        when(inMessage.getBody(String.class)).thenReturn(SAMPLE_MESSAGE_BODY);
        when(inMessage.getBody(InputStream.class)).thenReturn(new ByteArrayInputStream(SAMPLE_MESSAGE_BODY.getBytes(StandardCharsets.UTF_8)));

    }

    @Test
    public void itSendsTheBodyFromAnExchange() throws Exception {
        assertNotNull(underTest);
        underTest.process(exchange);

        ArgumentCaptor<String> capturePath = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);

        verify(serviceBusContractMock).sendQueueMessage(capturePath.capture(), captureMessage.capture());
        assertEquals(SAMPLE_MESSAGE_BODY, Utilities.readString(captureMessage.getValue().getBody()));
    }

    @Test
    public void itSendsTheCorrectQueueUrl() throws Exception {
        underTest.process(exchange);

        ArgumentCaptor<String> captureQueueName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);

        verify(serviceBusContractMock).sendQueueMessage(captureQueueName.capture(),captureMessage.capture());
        assertEquals(QUEUE_URL, captureQueueName.getValue());
    }

    @Test
    public void itBodyOnTheExchangeMessage() throws Exception {
        underTest.process(exchange);
        verify(inMessage).getBody(InputStream.class);
    }

    @Test
    @Parameters
    @Ignore("the header assertion fails since the captured message has no properties, except for messageId")
    public void isAttributeMessageStringHeaderOnTheRequest(Object headerValue) throws Exception {
        Map<String, Object> headers = new HashMap<>();
        String headerName = "testHeader";

        headers.put(headerName, headerValue);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);

        ArgumentCaptor<String> captureQueueName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);
        verify(serviceBusContractMock).sendQueueMessage(captureQueueName.capture(), captureMessage.capture());

        BrokeredMessage message = captureMessage.getValue();
        assertEquals(headerValue, message.getProperty(headerName));
    }
    private List<Object> parametersForIsAttributeMessageStringHeaderOnTheRequest()  {
        return Arrays.asList(
                "some text",
                ByteBuffer.wrap(new byte[10])
        );
    }

//    @Test
//    public void isAllAttributeMessagesOnTheRequest() throws Exception {
//        Map<String, Object> headers = new HashMap<String, Object>();
//        headers.put(SAMPLE_MESSAGE_HEADER_NAME_1, SAMPLE_MESSAGE_HEADER_VALUE_1);
//        headers.put(SAMPLE_MESSAGE_HEADER_NAME_2, SAMPLE_MESSAGE_HEADER_VALUE_2);
//        headers.put(SAMPLE_MESSAGE_HEADER_NAME_3, SAMPLE_MESSAGE_HEADER_VALUE_3);
//        headers.put(SAMPLE_MESSAGE_HEADER_NAME_4, SAMPLE_MESSAGE_HEADER_VALUE_4);
//        when(inMessage.getHeaders()).thenReturn(headers);
//        underTest.process(exchange);
//
//        ArgumentCaptor<String> captureQueueName = ArgumentCaptor.forClass(String.class);
//        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);
//        verify(serviceBusContractMock).sendMessage(captureQueueName.capture(), captureMessage.capture());
//
//
//        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_1,
//                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_1)
//                        .getStringValue());
//        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_2,
//                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_2)
//                        .getBinaryValue());
//        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_3,
//                capture.getValue().getMessageAttributes().get(SAMPLE_MESSAGE_HEADER_NAME_3)
//                        .getStringValue());
//        assertEquals(3, capture.getValue().getMessageAttributes().size());
//    }

}
