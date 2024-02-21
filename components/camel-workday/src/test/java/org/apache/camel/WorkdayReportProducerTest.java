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
package org.apache.camel;

import org.apache.camel.component.workday.WorkdayComponent;
import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.camel.component.workday.WorkdayEndpoint;
import org.apache.camel.component.workday.producer.WorkdayReportProducer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class WorkdayReportProducerTest extends CamelTestSupport {

    @Test
    public void createProducerMinimalConfiguration() throws Exception {
        WorkdayComponent workdayComponent = context.getComponent("workday", WorkdayComponent.class);

        WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint) workdayComponent
                .createEndpoint("workday:report:/ISU_Camel/Custom_Report_Employees?" + "host=impl.workday.com" + "&tenant=camel"
                                + "&clientId=f7014d38-99d2-4969-b740-b5b62db6b46a"
                                + "&clientSecret=7dbaf280-3cea-11ea-b77f-2e728ce88125" + "&tokenRefresh=88689ab63cda"
                                + "&reportFormat=json");

        WorkdayConfiguration workdayConfiguration = workdayEndpoint.getWorkdayConfiguration();

        assertEquals(WorkdayConfiguration.Entity.report, workdayConfiguration.getEntity());
        assertEquals("/ISU_Camel/Custom_Report_Employees", workdayConfiguration.getPath());
        assertEquals("impl.workday.com", workdayConfiguration.getHost());
        assertEquals("camel", workdayConfiguration.getTenant());
        assertEquals("f7014d38-99d2-4969-b740-b5b62db6b46a", workdayConfiguration.getClientId());
        assertEquals("7dbaf280-3cea-11ea-b77f-2e728ce88125", workdayConfiguration.getClientSecret());
        assertEquals("88689ab63cda", workdayConfiguration.getTokenRefresh());
    }

    @Test
    public void createProducerNoHostConfiguration() {
        WorkdayComponent workdayComponent = context.getComponent("workday", WorkdayComponent.class);

        try {

            WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint) workdayComponent
                    .createEndpoint("workday:report:/ISU_Camel/Custom_Report_Employees?" + "tenant=camel"
                                    + "&clientId=f7014d38-99d2-4969-b740-b5b62db6b46a"
                                    + "&clientSecret=7dbaf280-3cea-11ea-b77f-2e728ce88125" + "&tokenRefresh=88689ab63cda"
                                    + "&format=json");
        } catch (Exception exception) {

            assertEquals(IllegalArgumentException.class, exception.getClass());
            assertEquals("Host must be specified", exception.getMessage());
            return;
        }

        fail("Required parameters validation failed.");
    }

    @Test
    public void createProducerUrlValidation() throws Exception {
        WorkdayComponent workdayComponent = context.getComponent("workday", WorkdayComponent.class);

        WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint) workdayComponent
                .createEndpoint("workday:report:/ISU_Camel/Custom_Report_Employees?" + "host=camel.myworkday.com"
                                + "&tenant=camel" + "&clientId=f7014d38-99d2-4969-b740-b5b62db6b46a"
                                + "&clientSecret=7dbaf280-3cea-11ea-b77f-2e728ce88125" + "&tokenRefresh=88689ab63cda"
                                + "&param=test1");

        WorkdayReportProducer workdayProducer = new WorkdayReportProducer(workdayEndpoint);
        String workdayUri = workdayProducer.prepareUri(workdayEndpoint.getWorkdayConfiguration());

        assertEquals(
                "https://camel.myworkday.com/ccx/service/customreport2/camel/ISU_Camel/Custom_Report_Employees?param=test1&format=json",
                workdayUri);
    }
}
