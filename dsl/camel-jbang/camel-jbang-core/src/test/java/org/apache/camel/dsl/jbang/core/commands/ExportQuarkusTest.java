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
package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ExportQuarkus#applicationPropertyLine(String, String)} key handling (CAMEL-25498): quarkus.*
 * keys are written as given, while camel.* keys stay dash-cased.
 */
class ExportQuarkusTest {

    private final ExportQuarkus command = new ExportQuarkus(new CamelJBangMain());

    @Test
    void quarkusKeysAreWrittenAsGiven() {
        // CAMEL-25498: user-chosen name segments inside quarkus.* keys must not be dash-converted, otherwise the
        // exported application gets other names than the project defines (datasource names, log categories, ...).
        assertEquals("quarkus.datasource.Orders.db-kind=postgresql",
                command.applicationPropertyLine("quarkus.datasource.Orders.db-kind", "postgresql"));
        assertEquals("quarkus.log.category.\"com.example.MyRoute\".level=DEBUG",
                command.applicationPropertyLine("quarkus.log.category.\"com.example.MyRoute\".level", "DEBUG"));
        assertEquals("quarkus.swagger-ui.urls.Camel=../openapi.json",
                command.applicationPropertyLine("quarkus.swagger-ui.urls.Camel", "../openapi.json"));
    }

    @Test
    void quarkusKebabKeyIsUnchanged() {
        assertEquals("quarkus.swagger-ui.path=/swagger",
                command.applicationPropertyLine("quarkus.swagger-ui.path", "/swagger"));
    }

    @Test
    void quarkusHawtioCamelCaseKeyIsWrittenAsGiven() {
        // hawtio option names are camel case and must stay as given; the general "quarkus.* as given" rule now covers
        // this without the former quarkus.hawtio.* special case.
        assertEquals("quarkus.hawtio.authenticationEnabled=false",
                command.applicationPropertyLine("quarkus.hawtio.authenticationEnabled", "false"));
    }

    @Test
    void camelKeysAreStillDashCased() {
        // camel.* keys are still dash-cased; Camel's property binding turns the dashes back at runtime.
        assertEquals("camel.main.routes-reload-enabled=true",
                command.applicationPropertyLine("camel.main.routesReloadEnabled", "true"));
    }

    @Test
    void camelServerAndManagementKeysAreSkipped() {
        assertNull(command.applicationPropertyLine("camel.server.port", "8080"));
        assertNull(command.applicationPropertyLine("camel.management.enabled", "true"));
    }
}
