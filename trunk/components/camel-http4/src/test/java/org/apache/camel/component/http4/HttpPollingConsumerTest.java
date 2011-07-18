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
package org.apache.camel.component.http4;

import java.net.SocketTimeoutException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http4.handler.DelayValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpPollingConsumerTest extends BaseHttpTest {

    @Test
    public void testReceive() throws Exception {
        String body = consumer.receiveBody("http4://" + getHostName() + ":" + getPort() + "/", String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceiveTimeout() throws Exception {
        String body = consumer.receiveBody("http4://" + getHostName() + ":" + getPort() + "/", 5000, String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceiveTimeoutTriggered() throws Exception {
        try {
            consumer.receiveBody("http4://" + getHostName() + ":" + getPort() + "/", 250, String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(SocketTimeoutException.class, e.getCause());
        }
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/", new DelayValidationHandler("GET", null, null, getExpectedContent(), 1000));
    }
}