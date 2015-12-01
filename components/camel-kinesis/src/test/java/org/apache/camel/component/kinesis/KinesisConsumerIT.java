package org.apache.camel.component.kinesis;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kinesis.utils.StringKinesisProducer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * Created by alina on 02.11.15.
 */
public class KinesisConsumerIT extends CamelTestSupport {
    final String region = "us-west-2";
    final String streamName = "test";
    final String partitionKey = "test_partition_key";
    final String applicationName = "CommonKinesisConsumer";
    private StringKinesisProducer stringKinesisProducer;


    @EndpointInject(uri = "kinesis:console.aws.amazon.com?region=" + region +
            "&streamName=" + streamName +
            "&partitionKey=" + partitionKey +
            "&applicationName=" + applicationName)
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    @Before
    public void before() {
        stringKinesisProducer = new StringKinesisProducer();
        stringKinesisProducer.start();
    }

    @After
    public void after() {
        stringKinesisProducer.destroy();
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
        Thread.sleep(30000);
        Executors.newFixedThreadPool(1)
                .submit(new Runnable() {
                            @Override
                            public void run() {
                                String msg = "message-";
                                stringKinesisProducer.execute(msg);
                            }
                        }
                );
        Thread.sleep(30000);
        to.expectedMessageCount(1);
        to.assertIsSatisfied(30000);
    }
}
