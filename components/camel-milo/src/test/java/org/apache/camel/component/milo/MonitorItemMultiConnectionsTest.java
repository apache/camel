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
package org.apache.camel.component.milo;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing monitoring items over multiple connections
 */
public class MonitorItemMultiConnectionsTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    private static final String MILO_CLIENT_ITEM_C1_1 = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI,
                                                                "myitem1")
                                                        + "&overrideHost=true";

    private static final String MILO_CLIENT_ITEM_C2_1 = "milo-client:opc.tcp://foo:bar2@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI,
                                                                "myitem1")
                                                        + "&overrideHost=true";

    private static final String MILO_CLIENT_ITEM_C3_1 = "milo-client:opc.tcp://foo2:bar@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI,
                                                                "myitem1")
                                                        + "&overrideHost=true";

    private static final String MOCK_TEST_1 = "mock:test1";
    private static final String MOCK_TEST_2 = "mock:test2";
    private static final String MOCK_TEST_3 = "mock:test3";

    private static final Logger LOG = LoggerFactory.getLogger(MonitorItemMultiConnectionsTest.class);

    @EndpointInject(MOCK_TEST_1)
    protected MockEndpoint test1Endpoint;

    @EndpointInject(MOCK_TEST_2)
    protected MockEndpoint test2Endpoint;

    @EndpointInject(MOCK_TEST_3)
    protected MockEndpoint test3Endpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);

                from(resolve(MILO_CLIENT_ITEM_C1_1)).to(MOCK_TEST_1);
                from(resolve(MILO_CLIENT_ITEM_C2_1)).to(MOCK_TEST_2);
                from(resolve(MILO_CLIENT_ITEM_C3_1)).to(MOCK_TEST_3);
            }
        };
    }

    /**
     * Monitor multiple connections, but only one has the correct credentials
     */
    @Test
    public void testMonitorItem1() throws Exception {
        // item 1 ... only this one receives
        this.test1Endpoint.setExpectedCount(1);
        this.test1Endpoint.setSleepForEmptyTest(5_000);

        // item 2
        this.test2Endpoint.setExpectedCount(0);
        this.test2Endpoint.setSleepForEmptyTest(5_000);

        // item 3
        this.test3Endpoint.setExpectedCount(0);
        this.test3Endpoint.setSleepForEmptyTest(5_000);

        // set server value
        this.producer1.sendBody("Foo");

        // assert
        MockEndpoint.assertIsSatisfied(context);
    }
}
