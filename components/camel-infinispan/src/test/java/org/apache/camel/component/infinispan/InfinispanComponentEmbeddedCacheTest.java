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
package org.apache.camel.component.infinispan;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class InfinispanComponentEmbeddedCacheTest extends InfinispanTestSupport {
    @EndpointInject("mock:embedded")
    private MockEndpoint mockEmbedded;

    @EndpointInject("mock:file")
    private MockEndpoint mockFile;

    @EndpointInject("mock:external")
    private MockEndpoint mockExternal;

    @Test
    public void sendAndRecieveToEmbededCache() throws InterruptedException {
        for (int i = 1; i <= 10; i++) {
            fluentTemplate()
                .to("direct:embedded")
                .withHeader(InfinispanConstants.KEY, "key:" + i)
                .withHeader(InfinispanConstants.VALUE, "Hey Camel!" + i)
                .send();

            if (i % 2 == 0) {
                fluentTemplate()
                    .to("direct:file")
                    .withHeader(InfinispanConstants.KEY, "key:" + i)
                    .withHeader(InfinispanConstants.VALUE, "Hey Infinispan!" + i)
                    .send();
            }
            if (i % 3 == 0) {
                namedCache("test").put("key:" + i, "Hey cache!" + i);
            }
        }

        mockEmbedded.expectedBodiesReceived("10");
        mockFile.expectedBodiesReceived("5");
        mockExternal.expectedBodiesReceived("3");
        fluentTemplate()
            .to("direct:embedded-get")
            .withHeader(InfinispanConstants.OPERATION, InfinispanConstants.RESULT_HEADER)
            .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.SIZE)
            .send();

        fluentTemplate().to("direct:file-get")
            .withHeader(InfinispanConstants.OPERATION, InfinispanConstants.RESULT_HEADER)
            .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.SIZE)
            .send();

        fluentTemplate().to("direct:external")
            .withHeader(InfinispanConstants.OPERATION, InfinispanConstants.RESULT_HEADER)
            .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.SIZE)
            .send();

        mockEmbedded.assertIsSatisfied();
        mockFile.assertIsSatisfied();
        mockExternal.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // random order of routes with different endpoints tests are needed for checking of proper containerCache initialization
        return new RouteBuilder() {
            public void configure() {
                from("direct:embedded").to("infinispan:test");
                from("direct:file").to("infinispan:test?configurationUri=cache-configuration.xml");
                from("direct:external").to("infinispan:test?cacheContainer=#cacheContainer").to("mock:external");
                from("direct:file-get").to("infinispan:test?configurationUri=cache-configuration.xml&operation=GET").to("mock:file");
                from("direct:embedded-get").to("infinispan:test?operation=GET").to("mock:embedded");
            }
        };
    }
}
