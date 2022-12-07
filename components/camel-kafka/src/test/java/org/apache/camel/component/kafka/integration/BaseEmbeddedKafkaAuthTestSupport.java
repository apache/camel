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
package org.apache.camel.component.kafka.integration;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tags({ @Tag("non-abstract") })
public abstract class BaseEmbeddedKafkaAuthTestSupport extends AbstractKafkaTestSupport {
    @RegisterExtension
    public static ContainerLocalAuthKafkaService service = new ContainerLocalAuthKafkaService("/kafka-jaas.config");

    protected static AdminClient kafkaAdminClient;

    @BeforeAll
    public static void beforeClass() {
        AbstractKafkaTestSupport.setServiceProperties(service);
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = createAdminClient();
        }
    }

    protected Properties getDefaultProperties() {
        return getDefaultProperties(service);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createCamelContextFromService(service);
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }

    private static AdminClient createAdminClient() {
        return createAdminClient(service);
    }
}
