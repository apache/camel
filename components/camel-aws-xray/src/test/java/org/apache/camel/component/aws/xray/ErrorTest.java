/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws.xray;

import java.lang.invoke.MethodHandles;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorTest extends CamelAwsXRayTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // FIXME: check why processors invoked in onRedelivery do not generate a subsegment
    public ErrorTest() {
        super(
            TestDataBuilder.createTrace()
                .withSegment(TestDataBuilder.createSegment("start")
                    .withSubsegment(TestDataBuilder.createSubsegment("bean:TraceBean"))
                    .withSubsegment(TestDataBuilder.createSubsegment("bean:TraceBean"))
                    .withSubsegment(TestDataBuilder.createSubsegment("bean:TraceBean"))
                    .withSubsegment(TestDataBuilder.createSubsegment("bean:TraceBean"))
                    .withSubsegment(TestDataBuilder.createSubsegment("process:ExceptionProcessor"))
                )
        );
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(Exception.class)
                    .process(new ExceptionProcessor())
                    .maximumRedeliveries(3)
                    .redeliveryDelay(200)
                    .useExponentialBackOff()
                    .backOffMultiplier(1.5D)
                    .onRedelivery(new ExceptionRetryProcessor())
                    .handled(true)
                    .log(LoggingLevel.WARN, "Caught error while performing task. Reason: ${exception.message} Stacktrace: ${exception.stacktrace}")
                    .end();

                from("direct:start").routeId("start")
                    .log("start has been called")
                    .bean(TraceBean.class)
                    .delay(simple("${random(1000,2000)}"))
                    .to("seda:otherRoute")
                    .to("mock:end");

                from("seda:otherRoute").routeId("otherRoute")
                    .log("otherRoute has been called")
                    .delay(simple("${random(0,500)}"));
            }
        };
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new TraceAnnotatedTracingStrategy();
    }

    @Test
    public void testRoute() throws Exception {
        template.requestBody("direct:start", "Hello");

        verify();
    }

    @XRayTrace
    public static class TraceBean {

        @Handler
        public String convertBodyToUpperCase(@Body String body) throws Exception {
            throw new Exception("test");
        }

        @Override
        public String toString() {
            return "TraceBean";
        }
    }

    @XRayTrace
    public static class ExceptionProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            Exception ex = (Exception)exchange.getProperties().get(Exchange.EXCEPTION_CAUGHT);
            LOG.debug("Processing caught exception {}", ex.getLocalizedMessage());
            exchange.getIn().getHeaders().put("HandledError", ex.getLocalizedMessage());
        }

        @Override
        public String toString() {
            return "ExceptionProcessor";
        }
    }

    @XRayTrace
    public static class ExceptionRetryProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            Exception ex = (Exception)exchange.getProperties().get(Exchange.EXCEPTION_CAUGHT);
            LOG.debug(">> Attempting redelivery of handled exception {} with message: {}",
                ex.getClass().getSimpleName(), ex.getLocalizedMessage());
        }

        @Override
        public String toString() {
            return "ExceptionRetryProcessor";
        }
    }
}