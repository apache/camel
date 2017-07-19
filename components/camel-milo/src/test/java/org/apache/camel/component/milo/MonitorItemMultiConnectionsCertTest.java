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
package org.apache.camel.component.milo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Testing monitoring items over multiple connections
 */
public class MonitorItemMultiConnectionsCertTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    // with key
    private static final String MILO_CLIENT_ITEM_C1_1 = "milo-client:tcp://foo:bar@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem1")
                                                        + "&keyStoreUrl=file:src/test/resources/cert/cert.p12&keyStorePassword=pwd1&keyPassword=pwd1";

    // with wrong password
    private static final String MILO_CLIENT_ITEM_C2_1 = "milo-client:tcp://foo:bar2@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem1");

    // without key, clientId=1
    private static final String MILO_CLIENT_ITEM_C3_1 = "milo-client:tcp://foo:bar@localhost:@@port@@?clientId=1&node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem1");

    private static final String MOCK_TEST_1 = "mock:test1";
    private static final String MOCK_TEST_2 = "mock:test2";
    private static final String MOCK_TEST_3 = "mock:test3";

    @EndpointInject(uri = MOCK_TEST_1)
    protected MockEndpoint test1Endpoint;

    @EndpointInject(uri = MOCK_TEST_2)
    protected MockEndpoint test2Endpoint;

    @EndpointInject(uri = MOCK_TEST_3)
    protected MockEndpoint test3Endpoint;

    @Produce(uri = DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Override
    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        super.configureMiloServer(server);

        final Path baseDir = Paths.get("target/testing/cert/default");
        final Path trusted = baseDir.resolve("trusted");

        Files.createDirectories(trusted);
        Files.copy(Paths.get("src/test/resources/cert/certificate.der"), trusted.resolve("certificate.der"), REPLACE_EXISTING);

        server.setServerCertificate(loadDefaultTestKey());
        server.setDefaultCertificateValidator(baseDir.toFile());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
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
        this.assertMockEndpointsSatisfied();
    }
}
