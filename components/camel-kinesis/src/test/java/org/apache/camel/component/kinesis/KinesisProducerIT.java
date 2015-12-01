package org.apache.camel.component.kinesis;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.model.Record;
import com.beust.jcommander.internal.Lists;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kinesis.utils.StringKinesisConsumer;
import org.apache.camel.component.kinesis.utils.StringRecordProcessor;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * Created by alina on 02.11.15.
 */
public class KinesisProducerIT extends CamelTestSupport {
    public static final String SENDING_MESSAGE = "Test message";

    public static final String region = "us-west-2";
    public static final String streamName = "test";
    public static final String partitionKey = "test_partition_key";
    public static final String applicationName = "CommonKinesisConsumer";

    @EndpointInject(uri = "kinesis:console.aws.amazon.com?region=" + region +
            "&streamName=" + streamName +
            "&partitionKey=" + partitionKey +
            "&applicationName=" + applicationName)
    private Endpoint to;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;


    @Before
    public void before() {
    }

    @After
    public void after() {
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(to);
            }
        };
    }

    @Test
    public void producedMessageIsReceivedByKafka() throws InterruptedException, IOException {
        //initialize consumer
        final List<String> messages = Lists.newArrayList();

        ExecutorService service = Executors.newFixedThreadPool(1);
        service.submit(new Runnable() {
            @Override
            public void run() {
                new StringKinesisConsumer() {
                    @Override
                    public IRecordProcessorFactory getRecordProcessorFactory() {
                        return this;
                    }

                    @Override
                    public IRecordProcessor createProcessor() {
                        return new StringRecordProcessor() {
                            @Override
                            public void processRecords(ProcessRecordsInput processRecordsInput) {
                                for (Record record : processRecordsInput.getRecords()) {
                                    messages.add(new String(record.getData().array()));
                                }
                            }
                        };
                    }
                }.execute();
            }
        });
        //wait initialisation consumer
        Thread.sleep(30000);

        //send messages
        ExecutorService service2 = Executors.newFixedThreadPool(1);
        service2.submit(new Runnable() {
            @Override
            public void run() {
                template.sendBodyAndHeader(SENDING_MESSAGE, "", "1");
            }
        });

        //wait receiving message
        Thread.sleep(50000);
        assertThat(messages, hasSize(1));
        assertThat(messages, contains(SENDING_MESSAGE));
    }
}
