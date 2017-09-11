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

import org.apache.camel.CamelContext;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.springboot.ReactiveStreamsComponentAutoConfiguration;
import org.apache.camel.component.reactive.streams.springboot.ReactiveStreamsServiceAutoConfiguration;
import org.apache.camel.component.reactive.streams.springboot.test.support.ReactiveStreamsServiceTestSupport;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@SpringBootTest(
    classes = {
        ReactiveStreamsNamedEngineTest.TestConfiguration.class
    },
    properties = {
        "camel.component.reactive-streams.service-type=my-engine"
    }
)
public class ReactiveStreamsNamedEngineTest {
    @Autowired
    private CamelContext context;
    @Autowired
    private CamelReactiveStreamsService reactiveStreamsService;

    @Test
    public void testAutoConfiguration() throws InterruptedException {
        CamelReactiveStreamsService service = CamelReactiveStreams.get(context);
        Assert.assertTrue(service instanceof MyEngine);
        Assert.assertEquals(service, reactiveStreamsService);
    }

    @Component("my-engine")
    static class MyEngine extends ReactiveStreamsServiceTestSupport {
        public MyEngine() {
            super("my-engine");
        }
    }

    @Configuration
    public static class TestConfiguration {
    }
}

