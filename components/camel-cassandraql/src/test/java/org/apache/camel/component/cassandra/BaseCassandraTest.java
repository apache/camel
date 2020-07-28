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
package org.apache.camel.component.cassandra;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

public abstract class BaseCassandraTest extends CamelTestSupport {

    public static final String KEYSPACE_NAME = "camel_ks";
    public static final String DATACENTER_NAME = "datacenter1";
    private static final int ORIGINAL_PORT = 9042;

    private static GenericContainer<?>  container;
    private CqlSession session;


    @BeforeAll
    public static void beforeAll() {
        container = new CassandraContainer().withInitScript("initScript.cql").withNetworkAliases("cassandra").withExposedPorts(ORIGINAL_PORT);
        container.start();
    }

    @AfterAll
    public static void afterAll() {
        try {
            if (container != null) {
                container.stop();
            }
        } catch (Exception e) {
            // ignored
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);

        executeScript("BasicDataSet.cql");
    }

    public void executeScript(String pathToScript) throws IOException {
        String s = IOUtils.toString(getClass().getResourceAsStream("/" + pathToScript), "UTF-8");
        String[] statements = s.split(";");
        for (int i = 0; i < statements.length; i++) {
            if (!statements[i].isEmpty()) {
                executeCql(statements[i]);
            }
        }
    }

    public void executeCql(String cql) {
        getSession().execute(cql);
    }

    @Override
    protected void doPostTearDown() throws Exception {
        super.doPostTearDown();

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
            InetSocketAddress endpoint = new InetSocketAddress(container.getContainerIpAddress(),  container.getMappedPort(ORIGINAL_PORT));
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
        return container.getContainerIpAddress() + ":" + container.getMappedPort(ORIGINAL_PORT);
    }
}
