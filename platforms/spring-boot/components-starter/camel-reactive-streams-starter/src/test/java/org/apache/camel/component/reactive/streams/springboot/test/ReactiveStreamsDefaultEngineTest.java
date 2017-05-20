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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.DefaultCamelReactiveStreamsService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Checking that the deadlock does not occur when the {@code CamelReactiveStreamsService} is not injected anywhere.
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        ReactiveStreamsDefaultEngineTest.class
    }
)
public class ReactiveStreamsDefaultEngineTest {
    @Autowired
    private CamelContext context;

    @Test
    public void testAutoConfiguration() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:data")
                        .log("${body}");
            }
        }.addRoutesToCamelContext(context);

        Assert.assertTrue(context.getStatus().isStarted());
        CamelReactiveStreamsService service = CamelReactiveStreams.get(context);
        Assert.assertTrue(service instanceof DefaultCamelReactiveStreamsService);
    }

    @Configuration
    public static class TestConfiguration {
    }
}

