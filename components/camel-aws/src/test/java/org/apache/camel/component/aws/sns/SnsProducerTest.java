package org.apache.camel.component.aws.sns;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.GetRecordsRequest;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.aws.firehose.KinesisFirehoseEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SnsProducerTest {

    @Mock
    private Exchange exchange;
    @Mock
    private SnsEndpoint endpoint;
    private SnsProducer producer;

    @Before
    public void setUp() {
        producer = new SnsProducer(endpoint);

        when(endpoint.getHeaderFilterStrategy()).thenReturn(new SnsHeaderFilterStrategy());
    }

    @Test
    public void translateAttributes() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("key1", null);
        headers.put("key2", "");
        headers.put("key3", "value3");

        Map<String, MessageAttributeValue> translateAttributes = producer.translateAttributes(headers, exchange);

        assertThat(translateAttributes.size(), is(1));
        assertThat(translateAttributes.get("key3").getDataType(), is("String"));
        assertThat(translateAttributes.get("key3").getStringValue(), is("value3"));
    }
}