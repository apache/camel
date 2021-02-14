package org.apache.camel.component.google.storage.unit;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerLocalTest extends GoogleCloudStorageBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerLocalTest.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("mock:consumedObjects")
    private MockEndpoint consumedObjects;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                String endpoint = "google-storage://myCamelBucket?autoCreateBucket=true";

                from("direct:putObject")
                        .startupOrder(1)
                        .to(endpoint)
                        .to("mock:result");

                from("google-storage://myCamelBucket?"
                     + "moveAfterRead=true"
                     + "&destinationBucket=camelDestinationBucket"
                     + "&autoCreateBucket=true"
                     + "&deleteAfterRead=true"
                     + "&includeBody=true")
                             .startupOrder(2)
                             //.log("consuming: ${header.CamelGoogleCloudStorageBucketName}/${header.CamelGoogleCloudStorageObjectName}, body=${body}")
                             .to("mock:consumedObjects");

            }
        };
    }

    @Test
    public void sendIn() throws Exception {

        final int NUMBER_OF_FILES = 3;

        result.expectedMessageCount(NUMBER_OF_FILES);
        consumedObjects.expectedMessageCount(NUMBER_OF_FILES);

        for (int i = 0; i < NUMBER_OF_FILES; i++) {
            final String filename = String.format("file_%s.txt", i);
            final String body = String.format("body_%s", i);
            //upload a file
            template.send("direct:putObject", exchange -> {
                exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, filename);
                exchange.getIn().setBody(body);
            });
        }

        Thread.sleep(5000);
        assertMockEndpointsSatisfied();

    }

}
