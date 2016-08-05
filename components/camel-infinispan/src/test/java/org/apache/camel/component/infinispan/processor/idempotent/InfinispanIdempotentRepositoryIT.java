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
package org.apache.camel.component.infinispan.processor.idempotent;

import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.Test;

public class InfinispanIdempotentRepositoryIT extends CamelTestSupport {

    @Test
    public void producerQueryOperationWithoutQueryBuilder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        final String messageId = UUID.randomUUID().toString();
        IntStream.range(0, 10).forEach(
            i -> template().sendBodyAndHeader("direct:start", "message-" + i, "MessageID", messageId)
        );

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .idempotentConsumer(
                        header("MessageID"),
                        new InfinispanIdempotentRepository(
                            new RemoteCacheManager(
                                new ConfigurationBuilder()
                                    .addServers("localhost")
                                    .build(),
                                true
                            ),
                            "idempotent"
                        )
                    )
                    .skipDuplicate(true)
                    .to("log:org.apache.camel.component.infinispan.processor.idempotent?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");
            }
        };
    }
}
