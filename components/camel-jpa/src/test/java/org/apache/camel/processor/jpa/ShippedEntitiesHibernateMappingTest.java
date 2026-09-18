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
package org.apache.camel.processor.jpa;

import org.apache.camel.processor.idempotent.jpa.MessageProcessed;
import org.apache.camel.processor.keyvalue.jpa.KeyValueEntry;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies in every build (not only with -Dhibernate) that Hibernate can map every {@code @Entity} shipped in the
 * camel-jpa jar. Runtimes such as Quarkus auto-discover these entities from the classpath and map them all with
 * Hibernate in a single persistence unit, so a mapping problem in any one of them breaks applications that merely have
 * camel-jpa on the classpath and never use the entity themselves (CAMEL-24604: a derived getter without a setter made
 * Hibernate fail with "Could not locate setter method for property 'expired'").
 * <p>
 * All shipped entities are mapped together, the way an auto-discovering runtime does it. Uses the native Hibernate
 * bootstrap on purpose: it does not go through jakarta.persistence provider resolution, so the rest of the test suite
 * keeps using the provider selected by the active maven profile.
 */
class ShippedEntitiesHibernateMappingTest {

    @Test
    void hibernateMustBeAbleToMapAllShippedEntities() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:camelJpaEntityMapping")
                .build();
        try {
            assertDoesNotThrow(() -> new MetadataSources(registry)
                    .addAnnotatedClass(KeyValueEntry.class)
                    .addAnnotatedClass(MessageProcessed.class)
                    .buildMetadata()
                    .buildSessionFactory()
                    .close(),
                    "Hibernate should be able to build a SessionFactory for the entities shipped in camel-jpa");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
