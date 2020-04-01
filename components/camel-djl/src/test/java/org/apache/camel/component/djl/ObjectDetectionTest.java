package org.apache.camel.component.djl;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ObjectDetectionTest extends CamelTestSupport {

    @Test
    public void testDJL() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/resources/data/detect?recursive=true&noop=true")
                        .convertBodyTo(byte[].class)
                        .to("djl:cv/object_detection?artifactId=ai.djl.mxnet:ssd:0.0.1")
                        .log("${header.CamelFileName} = ${body}")
                        .to("mock:result");
            }
        };
    }

}
