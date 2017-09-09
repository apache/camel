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
package org.apache.camel.spring.boot;

import java.io.Closeable;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Test class illustrating the invalid shutdown sequence when using the autoconfiguration
 * provided by <code>camel-spring-boot</code>.
 * <p>
 * This is caused by the {@link TypeConversionConfiguration} class registering a
 * {@link TypeConverter} (of actual type {@link DefaultTypeConverter}) in the Spring
 * {@link ApplicationContext}. Its '{@code public void shutdown()}' method is inferred as a destroy-method by <i>Spring</i>,
 * which will thus be called before the {@link CamelContext} shutdown
 * when the context is closed.
 * <p>
 * As a consequence, any inflight message that should be processed during the graceful
 * shutdown period of Camel won't have access to any type conversion support.
 */
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
// Let the CamelAutoConfiguration do all the configuration for us
// including the TypeConverter registration into the ApplicationContext
@SpringBootTest(classes = {CamelAutoConfiguration.class, CamelSpringBootShutdownTest.TestRouteConfiguration.class})
public class CamelSpringBootShutdownTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private ProducerTemplate template;

    @Test
    public void test1() throws Exception {
        try {
            // Send a String body that need to be converted to an InputStream
            template.sendBody("direct:start", "42");
        } catch (CamelExecutionException e) {
            // unwrap Exception
            throw (Exception) e.getCause();
        }
    }

    @Test
    public void test2() throws Exception {
        try {
            // Starts a Thread to close the context in 500 ms
            new DelayedCloser(context, 500).start();
            // Send the same body, and let the context be closed before the processing happens
            template.sendBody("direct:start", "42");
        } catch (CamelExecutionException e) {
            // unwrap Exception
            throw (Exception) e.getCause();
        }
    }

    public static class DelayedCloser extends Thread {

        private final long sleep;
        private final Closeable closeable;

        public DelayedCloser(Closeable closeable, long sleep) {
            this.closeable = closeable;
            this.sleep = sleep;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(sleep);
                closeable.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static class TestRouteConfiguration {
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                            // delay the processing to force the exchange to be inflight
                            // during the context shutdown
                            .delay(1000)
                            .convertBodyTo(InputStream.class)
                            .to("log:route-log");
                }
            };
        }
    }

}