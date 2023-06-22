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
package org.apache.camel.component.cassandra.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.cassandra.services.CassandraLocalContainerService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class BaseCassandra implements ConfigurableRoute, CamelTestSupportHelper {

    @Order(1)
    @RegisterExtension
    public static CassandraLocalContainerService service;

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    public static final String KEYSPACE_NAME = "camel_ks";
    public static final String DATACENTER_NAME = "datacenter1";

    protected CamelContext context = camelContextExtension.getContext();

    private CqlSession session;

    static {
        service = new CassandraLocalContainerService();

        service.getContainer()
                .withInitScript("initScript.cql")
                .withNetworkAliases("cassandra");
    }

    @BeforeEach
    public void executeScript() throws Exception {
        executeScript("BasicDataSet.cql");
    }

    public void executeScript(String pathToScript) throws IOException {
        String s = IOHelper.stripLineComments(Paths.get("src/test/resources/" + pathToScript), "--", true);
        String[] statements = s.split(";");
        for (int i = 0; i < statements.length; i++) {
            if (!statements[i].isBlank()) {
                executeCql(statements[i]);
            }
        }
    }

    public void executeCql(String cql) {
        getSession().execute(cql);
    }

    @AfterEach
    protected void doPostTearDown() throws Exception {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            // ignored
        }
    }

    public CqlSession getSession() {
        if (session == null) {
            InetSocketAddress endpoint
                    = new InetSocketAddress(service.getCassandraHost(), service.getCQL3Port());
            //create a new session
            session = CqlSession.builder()
                    .withLocalDatacenter(DATACENTER_NAME)
                    .withKeyspace(KEYSPACE_NAME)
                    .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                            .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(5)).build())
                    .addContactPoint(endpoint).build();
        }
        return session;
    }

    public String getUrl() {
        return service.getCQL3Endpoint();
    }

    protected abstract RouteBuilder createRouteBuilder();

    @Override
    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }
}
