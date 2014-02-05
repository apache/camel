package org.apache.camel.component.kafka;

import java.io.IOException;
import java.util.Properties;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

/**
 * @author Stephen Samuel
 *         <p/>
 *         <p/>
 *         The Producer IT tests require a Kafka broker running on 9092 and a zookeeper instance running on 2181.
 *         The broker must have a topic called test created.
 */
public class KafkaConsumerIT extends CamelTestSupport {

    public static final String TOPIC = "test";

    @EndpointInject(uri = "kafka:localhost:9092?topic=" + TOPIC +
            "&zookeeperHost=localhost&zookeeperPort=2181&groupId=group1")
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    private Producer<String, String> producer;

    @Before
    public void before() {
        Properties props = new Properties();
        props.put("metadata.broker.list", "localhost:9092");
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        props.put("partitioner.class", "org.apache.camel.component.kafka.SimplePartitioner");
        props.put("request.required.acks", "1");

        ProducerConfig config = new ProducerConfig(props);
        producer = new Producer<String, String>(config);
    }

    @After
    public void after() {
        producer.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(from).to(to);
            }
        };
    }

    @Test
    public void kaftMessageIsConsumedByCamel() throws InterruptedException, IOException {
        to.expectedMessageCount(5);
        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            KeyedMessage<String, String> data = new KeyedMessage<String, String>(TOPIC, "1", msg);
            producer.send(data);
        }
        to.assertIsSatisfied(3000);
    }
}

