/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.apache.camel.component.dhis2;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

import org.hisp.dhis.api.model.v2_38_1.OrganisationUnit;
import org.hisp.dhis.api.model.v2_38_1.OrganisationUnitLevel;
import org.hisp.dhis.api.model.v2_38_1.WebMessage;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public final class Environment {
    private Environment() {

    }

    private static final Network NETWORK = Network.newNetwork();

    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

    private static final GenericContainer<?> DHIS2_CONTAINER;

    private static final Dhis2Client DHIS2_CLIENT;

    public static final String ORG_UNIT_ID;

    static {
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:12-3.2-alpine").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("dhis2")
                .withNetworkAliases("db")
                .withUsername("dhis")
                .withPassword("dhis").withNetwork(NETWORK);

        POSTGRESQL_CONTAINER.start();

        DHIS2_CONTAINER = new GenericContainer<>(
                "dhis2/core:2.37.4-tomcat-8.5.34-jre8-alpine")
                .dependsOn(POSTGRESQL_CONTAINER)
                .withClasspathResourceMapping("dhis.conf", "/DHIS2_home/dhis.conf", BindMode.READ_WRITE)
                .withNetwork(NETWORK).withExposedPorts(8080)
                .waitingFor(
                        new HttpWaitStrategy().forStatusCode(200).withStartupTimeout(Duration.ofSeconds(120)))
                .withEnv("WAIT_FOR_DB_CONTAINER", "db" + ":" + 5432 + " -t 0");

        DHIS2_CONTAINER.start();

        DHIS2_CLIENT = Dhis2ClientBuilder.newClient(
                "http://" + Environment.getDhis2Container().getHost() + ":" + Environment.getDhis2Container()
                        .getFirstMappedPort() + "/api",
                "admin", "district").build();

        ORG_UNIT_ID = createOrgUnit();
        createOrgUnitLevel();
        addOrgUnitToUser(ORG_UNIT_ID);
    }

    private static String createOrgUnit() {
        OrganisationUnit organisationUnit = new OrganisationUnit().withName("Acme").withShortName("Acme")
                .withOpeningDate(new Date());

        return (String) ((Map<String, Object>) DHIS2_CLIENT.post("organisationUnits").withResource(organisationUnit)
                .transfer()
                .returnAs(WebMessage.class).getResponse().get()).get("uid");
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
