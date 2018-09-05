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
package org.apache.camel.component.reactive.streams.springboot.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConstants;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.DefaultCamelReactiveStreamsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        ReactiveStreamsAutoConfigurationTest.TestConfiguration.class
    },
    properties = {
        "camel.component.reactive-streams.internal-engine-configuration.thread-pool-name=rs-test"
    }
)
public class ReactiveStreamsAutoConfigurationTest {
    @Autowired
    private CamelContext context;
    @Autowired
    private CamelReactiveStreamsService reactiveStreamsService;

    @Test
    public void testConfiguration() throws InterruptedException {
        CamelReactiveStreamsService service = CamelReactiveStreams.get(context);
        assertTrue(service instanceof DefaultCamelReactiveStreamsService);
        assertEquals(service, reactiveStreamsService);

        ReactiveStreamsComponent component = context.getComponent(ReactiveStreamsConstants.SCHEME, ReactiveStreamsComponent.class);
        assertEquals("rs-test", component.getInternalEngineConfiguration().getThreadPoolName());
    }

    @Test
    public void testService() throws InterruptedException {
        CamelReactiveStreamsService service = CamelReactiveStreams.get(context);
        CountDownLatch latch = new CountDownLatch(1);
        String[] res = new String[1];
        Throwable[] error = new Throwable[1];
        Publisher<String> string = service.fromStream("stream", String.class);

        string.subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(100);
            }

            @Override
            public void onNext(String s) {
                res[0] = s;
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error[0] = throwable;
            }

            @Override
            public void onComplete() {
            }
        });

        context.createFluentProducerTemplate().to("direct:endpoint").withBody("Hello").send();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("Hello", res[0]);
        Thread.sleep(100);
        assertNull(error[0]);
    }

    @Component
    static class Routes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:endpoint")
                .to("reactive-streams:stream");
        }
    }

    @Configuration
    public static class TestConfiguration {
    }
}

