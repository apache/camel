/*
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
package org.apache.camel.component.netty;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for CAMEL-9527
 */
@Isolated
class ErrorDuringGracefullShutdownTest extends BaseNettyTest {

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                // mock server
                from("netty:tcp://0.0.0.0:{{port}}?textline=true&disconnect=false")
                        .log("Got request ${body}")
                        .setBody(constant("response"));

                from("direct:req")
                        .to("netty:tcp://127.0.0.1:{{port}}?textline=true");
            }
        };
    }

    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    @Test
    void shouldNotTriggerErrorDuringGracefullShutdown() throws Exception {
        // given: successful request
        assertThat(template.requestBody("direct:req", "test", String.class)).isEqualTo("response");

        // when: context is closed
        context().close();

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> context.getStatus(), Matchers.equalTo(ServiceStatus.Stopped));

        // then: there should be no entries in log indicating that the callback was called twice
        assertThat(LogCaptureAppender.hasEventsFor(DefaultErrorHandler.class)).isFalse();
    }
}
