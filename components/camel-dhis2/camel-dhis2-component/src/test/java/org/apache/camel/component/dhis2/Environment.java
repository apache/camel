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
package org.apache.camel.component.dhis2;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hisp.dhis.api.model.v40_2_2.ApiToken;
import org.hisp.dhis.api.model.v40_2_2.OrganisationUnit;
import org.hisp.dhis.api.model.v40_2_2.OrganisationUnitLevel;
import org.hisp.dhis.api.model.v40_2_2.WebMessage;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public final class Environment {

    public static final Dhis2Client DHIS2_CLIENT;

    public static final String PERSONAL_ACCESS_TOKEN;

    public static final String ORG_UNIT_ID_UNDER_TEST;

    private static final Network NETWORK = Network.newNetwork();

    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

    private static final GenericContainer<?> DHIS2_CONTAINER;

    private Environment() {

    }

    static {
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:12-3.2-alpine").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("dhis2")
                .withNetworkAliases("db")
                .withUsername("dhis")
                .withPassword("dhis").withNetwork(NETWORK);

        POSTGRESQL_CONTAINER.start();

        DHIS2_CONTAINER = new GenericContainer<>(
                "dhis2/core:2.40.2.1")
                .dependsOn(POSTGRESQL_CONTAINER)
                .withClasspathResourceMapping("dhis.conf", "/opt/dhis2/dhis.conf", BindMode.READ_WRITE)
                .withNetwork(NETWORK).withExposedPorts(8080)
                .waitingFor(
                        new HttpWaitStrategy().forStatusCode(200).withStartupTimeout(Duration.ofSeconds(360)))
                .withEnv("WAIT_FOR_DB_CONTAINER", "db" + ":" + 5432 + " -t 0");

        DHIS2_CONTAINER.start();

        DHIS2_CLIENT = Dhis2ClientBuilder.newClient(
                "http://" + Environment.getDhis2Container().getHost() + ":" + Environment.getDhis2Container()
                        .getFirstMappedPort() + "/api",
                "admin", "district").build();

        createOrgUnit("EvilCorp");
        ORG_UNIT_ID_UNDER_TEST = createOrgUnit("Acme");
        createOrgUnitLevel();
        addOrgUnitToUser(ORG_UNIT_ID_UNDER_TEST);
        PERSONAL_ACCESS_TOKEN = createPersonalAccessToken();
    }

    private static String createPersonalAccessToken() {
        return DHIS2_CLIENT
                .post("apiToken")
                .withResource(
                        new ApiToken()
                                .withAttributes(
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "MethodAllowedList",
                                                        "allowedMethods",
                                                        List.of("GET", "POST", "PUT", "PATCH", "DELETE"))))
                                .withExpire(Long.MAX_VALUE))
                .transfer()
                .returnAs(WebMessage.class)
                .getResponse()
                .get().get("key");
    }

    private static String createOrgUnit(String name) {
        OrganisationUnit organisationUnit = new OrganisationUnit().withName(name).withShortName(name)
                .withOpeningDate(new Date());

        return DHIS2_CLIENT.post("organisationUnits").withResource(organisationUnit)
                .transfer()
                .returnAs(WebMessage.class).getResponse().get().get("uid");
    }

    private static void createOrgUnitLevel() {
        OrganisationUnitLevel organisationUnitLevel = new OrganisationUnitLevel().withName("Level 1")
                .with("level", 1);
        DHIS2_CLIENT.post("filledOrganisationUnitLevels").withResource(organisationUnitLevel).transfer();
    }

    private static void addOrgUnitToUser(String orgUnitId) {
        DHIS2_CLIENT.post("users/M5zQapPyTZI/organisationUnits/{organisationUnitId}", orgUnitId).transfer();
    }

    public static GenericContainer<?> getDhis2Container() {
        return DHIS2_CONTAINER;
    }

    public static Dhis2Client getDhis2Client() {
        return DHIS2_CLIENT;
    }
}
