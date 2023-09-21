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
package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JettyLogExceptionTest extends BaseJettyTest {

    @Test
    public void testLogException() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/foo");
        get.addHeader("Accept", "application/text");

        Appender appender = mock(Appender.class);
        when(appender.getName()).thenReturn("mockAppender");
        when(appender.isStarted()).thenReturn(true);
        Logger logger = (Logger) LogManager.getLogger(DefaultHttpBinding.class);
        logger.addAppender(appender);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(get);
        }

        ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
        verify(appender, atLeastOnce()).append(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(
                event -> event.getMessage().getFormattedMessage().equals(
                        "Server internal error response returned due to 'Camel cannot do this'")));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:{{port}}/foo?muteException=true&logException=true").to("mock:destination")
                        .throwException(new IllegalArgumentException("Camel cannot do this"));
            }
        };
    }
}
