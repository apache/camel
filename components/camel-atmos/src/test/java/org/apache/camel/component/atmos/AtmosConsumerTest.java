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
package org.apache.camel.component.atmos;

import java.util.Base64;

import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atmos.integration.consumer.AtmosScheduledPollGetConsumer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class AtmosConsumerTest extends CamelTestSupport {

    private String fake = Base64.getEncoder().encodeToString("fakeSecret".getBytes());

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("atmos:foo/get?remotePath=/path&fullTokenId=fakeToken&secretKey=%s&uri=https://fake/uri", fake)
                    .to("mock:test");
            }
        };
    }

    @Test
    public void shouldCreateGetConsumer() throws Exception {
        AtmosEndpoint endpoint = (AtmosEndpoint) context.getEndpoints().stream().filter(e -> e instanceof AtmosEndpoint).findFirst().orElse(null);
        assertNotNull(endpoint);

        Consumer consumer = endpoint.createConsumer(null);
        Assert.assertTrue(consumer instanceof AtmosScheduledPollGetConsumer);
        assertEquals("foo", endpoint.getConfiguration().getName());
    }

}
