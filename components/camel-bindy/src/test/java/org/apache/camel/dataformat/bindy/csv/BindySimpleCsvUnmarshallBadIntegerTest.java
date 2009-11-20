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
package org.apache.camel.dataformat.bindy.csv;

import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.Configuration;
import org.springframework.config.java.test.JavaConfigContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@ContextConfiguration(locations = "org.apache.camel.dataformat.bindy.csv.BindySimpleCsvUnmarshallBadIntegerTest$ContextConfig", loader = JavaConfigContextLoader.class)
public class BindySimpleCsvUnmarshallBadIntegerTest extends AbstractJUnit4SpringContextTests {

    private static final transient Log LOG = LogFactory.getLog(BindySimpleCsvUnmarshallBadIntegerTest.class);

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_MOCK_ERROR = "mock:error";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    protected ProducerTemplate template;

    private String record;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    @EndpointInject(uri = URI_MOCK_ERROR)
    private MockEndpoint error;

    @SuppressWarnings("unchecked")
    @Test
    @DirtiesContext
    public void testIntegerMessage() throws Exception {

        record = "10000,25.10";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        List<Map<String, org.apache.camel.dataformat.bindy.model.simple.oneclassmath.Math>> model = (List<Map<String, org.apache.camel.dataformat.bindy.model.simple.oneclassmath.Math>>)result
            .getExchanges().get(0).getIn().getBody();

        LOG.info(">>> Model generated : " + model.get(0).get("org.apache.camel.dataformat.bindy.model.simple.oneclassmath.Math").toString());

    }

    @Test
    @DirtiesContext
    public void testIntegerTooBigError() throws Exception {
        record = "1000000000000000000000000000000000000,25.10";

        template.sendBody(record);

        // We don't expect to have a message as an error will be raised
        result.expectedMessageCount(0);

        // Message has been delivered to the mock error
        error.expectedMessageCount(1);

        result.assertIsSatisfied();
        error.assertIsSatisfied();

        // and check that we have the caused exception stored
        Exception cause = error.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        TestSupport.assertIsInstanceOf(Exception.class, cause.getCause());
        assertEquals("String provided does not fit the Integer pattern defined or is not parseable, position : 1, line : 1", cause.getMessage());

    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {

        BindyCsvDataFormat orderBindyDataFormat = new BindyCsvDataFormat("org.apache.camel.dataformat.bindy.model.simple.oneclassmath");

        @Override
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() {

                    Tracer tracer = new Tracer();
                    tracer.setLogLevel(LoggingLevel.FATAL);
                    tracer.setLogName("org.apache.camel.bindy");
                    tracer.setLogStackTrace(true);
                    tracer.setTraceExceptions(true);

                    getContext().addInterceptStrategy(tracer);

                    // default should errors go to mock:error
                    errorHandler(deadLetterChannel(URI_MOCK_ERROR).redeliverDelay(0));

                    onException(Exception.class).maximumRedeliveries(0).handled(true);

                    from(URI_DIRECT_START).unmarshal(orderBindyDataFormat).to(URI_MOCK_RESULT);

                }
            };
        }
    }
}
