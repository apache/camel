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
package org.apache.camel.pgevent.integration;

import org.apache.camel.test.infra.jdbc.services.JDBCLocalContainerService;
import org.apache.camel.test.infra.jdbc.services.JDBCService;
import org.apache.camel.test.infra.jdbc.services.JDBCServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class PgEventITSupport extends CamelTestSupport {
    @RegisterExtension
    public static JDBCService service = JDBCServiceFactory
            .builder()
            .addLocalMapping(PgEventITSupport::createLocalService)
            .build();

    protected static final String TEST_MESSAGE_BODY = "Test Camel PGEvent";
    protected static final String POSTGRES_USER = "postgres";
    protected static final String POSTGRES_PASSWORD = "mysecretpassword";
    protected static final String POSTGRES_DB = "postgres";

    private static final Logger LOG = LoggerFactory.getLogger(PgEventITSupport.class);

    private static PostgreSQLContainer container;

    private static JDBCService createLocalService() {
        final String postgresImage = "postgres:13.0";

        container = new PostgreSQLContainer(postgresImage);

        container.withUsername(POSTGRES_USER)
                .withPassword(POSTGRES_PASSWORD)
                .withDatabaseName(POSTGRES_DB)
                .withLogConsumer(new Slf4jLogConsumer(LOG));

        return new JDBCLocalContainerService<>(container);
    }

    public Integer getMappedPort() {
        return container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    public String getHost() {
        return container.getHost();
    }

}
