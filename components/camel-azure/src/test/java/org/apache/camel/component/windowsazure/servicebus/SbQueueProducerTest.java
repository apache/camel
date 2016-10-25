package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by alan on 17/10/16.
 */
@RunWith(MockitoJUnitRunner.class)

public class SbQueueProducerTest {
    private static final String SAMPLE_MESSAGE_BODY = "this is a body";
    private static final String MESSAGE_ID = "11111111111111111111111111111111";
    private static final String QUEUE_URL = "some://queue/url";
    private static final String SAMPLE_MESSAGE_HEADER_NAME_1 = "header_name_1";
    private static final String SAMPLE_MESSAGE_HEADER_VALUE_1 = "heder_value_1";
    private static final String SAMPLE_MESSAGE_HEADER_NAME_2 = "header_name_2";
    private static final ByteBuffer SAMPLE_MESSAGE_HEADER_VALUE_2 = ByteBuffer.wrap(new byte[10]);
    private static final String SAMPLE_MESSAGE_HEADER_NAME_3 = "header_name_3";
    private static final String SAMPLE_MESSAGE_HEADER_VALUE_3 = "heder_value_3";
    private static final String SAMPLE_MESSAGE_HEADER_NAME_4 = "CamelHeader_1";
    private static final String SAMPLE_MESSAGE_HEADER_VALUE_4 = "testValue";

    Exchange exchange = mock(Exchange.class, RETURNS_DEEP_STUBS);

    @Mock private SbQueueEndpoint bbEndpoint;
    @Mock private ServiceBusContract serviceBusContractMock;
    @Mock private Message outMessage;
    @Mock private Message inMessage;

    private SbConfiguration sbConfiguration;

    private SbQueueProducer underTest;

    @Before
    public void setup() throws Exception {
        underTest = new SbQueueProducer(bbEndpoint);
        sbConfiguration = new SbConfiguration();
        sbConfiguration.setQueueName(QUEUE_URL);
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
        verify(serviceBusContractMock).sendMessage(capturePath.capture(), captureMessage.capture());
        assertEquals(SAMPLE_MESSAGE_BODY, Utilities.readString(captureMessage.getValue().getBody()));
    }
    @Test
    public void itSendsTheCorrectQueueUrl() throws Exception {
        underTest.process(exchange);

        ArgumentCaptor<String> captureQueueName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);
        verify(serviceBusContractMock).sendMessage(captureQueueName.capture(),captureMessage.capture());
        assertEquals(QUEUE_URL, captureQueueName.getValue());
    }

    @Test
    public void itBodyOnTheExchangeMessage() throws Exception {
        underTest.process(exchange);
        verify(inMessage).getBody(InputStream.class);
    }

    @Test
    public void isAttributeMessageStringHeaderOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_1, SAMPLE_MESSAGE_HEADER_VALUE_1);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);

        ArgumentCaptor<String> captureQueueName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);
        verify(serviceBusContractMock).sendMessage(captureQueueName.capture(), captureMessage.capture());

        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_1,
                captureMessage.getValue().getProperty(SAMPLE_MESSAGE_HEADER_NAME_1));
    }

    @Test
    public void isAttributeMessageByteBufferHeaderOnTheRequest() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SAMPLE_MESSAGE_HEADER_NAME_2, SAMPLE_MESSAGE_HEADER_VALUE_2);
        when(inMessage.getHeaders()).thenReturn(headers);
        underTest.process(exchange);

        ArgumentCaptor<String> captureQueueName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BrokeredMessage> captureMessage = ArgumentCaptor.forClass(BrokeredMessage.class);
        verify(serviceBusContractMock).sendMessage(captureQueueName.capture(), captureMessage.capture());

        assertEquals(SAMPLE_MESSAGE_HEADER_VALUE_2,
                captureMessage.getValue().getProperty(SAMPLE_MESSAGE_HEADER_NAME_2));
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
