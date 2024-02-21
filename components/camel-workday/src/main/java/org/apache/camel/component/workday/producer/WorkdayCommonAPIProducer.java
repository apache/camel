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
package org.apache.camel.component.workday.producer;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.camel.component.workday.WorkdayEndpoint;

/**
 * The Workday Common REST API producer.
 */
public class WorkdayCommonAPIProducer extends WorkdayDefaultProducer {

    public static final String WORKDAY_COMMON_API_URL_TEMPLATE = "https://%s/ccx/api/v1/%s%s";

    public static final String WORKDAY_ID_PATTERN = "([0-9a-f]{32})";

    public static final String WORKDAY_GENERIC_ID = "{ID}";

    private final Set<String> workdayValidEndpointSet;

    public WorkdayCommonAPIProducer(WorkdayEndpoint endpoint) {

        super(endpoint);

        this.workdayValidEndpointSet = new HashSet<>();
        this.workdayValidEndpointSet.add("/auditLogs");
        this.workdayValidEndpointSet.add("/auditLogs/{ID}");
        this.workdayValidEndpointSet.add("/businessTitleChanges/{ID}");
        this.workdayValidEndpointSet.add("/currencies");
        this.workdayValidEndpointSet.add("/currencies/{ID}");
        this.workdayValidEndpointSet.add("/customers/{ID}");
        this.workdayValidEndpointSet.add("/customers/{ID}/activities");
        this.workdayValidEndpointSet.add("/customers/{ID}/activities/{ID}");
        this.workdayValidEndpointSet.add("/jobChangeReasons");
        this.workdayValidEndpointSet.add("/jobChangeReasons/{ID}");
        this.workdayValidEndpointSet.add("/organizationTypes");
        this.workdayValidEndpointSet.add("/organizationTypes/{ID}");
        this.workdayValidEndpointSet.add("/organizations");
        this.workdayValidEndpointSet.add("/organizations/{ID}");
        this.workdayValidEndpointSet.add("/supervisoryOrganizations");
        this.workdayValidEndpointSet.add("/supervisoryOrganizations/{ID}");
        this.workdayValidEndpointSet.add("/supervisoryOrganizations/{ID}/workers");
        this.workdayValidEndpointSet.add("/supervisoryOrganizations/{ID}/workers/{ID}");
        this.workdayValidEndpointSet.add("/workers");
        this.workdayValidEndpointSet.add("/workers/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/businessTitleChanges");
        this.workdayValidEndpointSet.add("/workers/{ID}/businessTitleChanges/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/directReports");
        this.workdayValidEndpointSet.add("/workers/{ID}/directReports/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/history");
        this.workdayValidEndpointSet.add("/workers/{ID}/history/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/inboxTasks");
        this.workdayValidEndpointSet.add("/workers/{ID}/inboxTasks/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/organizations");
        this.workdayValidEndpointSet.add("/workers/{ID}/organizations/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/paySlips");
        this.workdayValidEndpointSet.add("/workers/{ID}/paySlips/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/supervisoryOrganizationsManaged");
        this.workdayValidEndpointSet.add("/workers/{ID}/supervisoryOrganizationsManaged/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/timeOffEntries");
        this.workdayValidEndpointSet.add("/workers/{ID}/timeOffEntries/{ID}");
        this.workdayValidEndpointSet.add("/workers/{ID}/timeOffPlans");
        this.workdayValidEndpointSet.add("/workers/{ID}/timeOffPlans/{ID}");

    }

    @Override
    public String prepareUri(WorkdayConfiguration configuration) throws Exception {

        String pathString = configuration.getPath();
        String genericPath = pathString.replaceAll(WORKDAY_ID_PATTERN, WORKDAY_GENERIC_ID);

        if (!this.workdayValidEndpointSet.contains(genericPath)) {
            throw new MalformedURLException(
                    String.format("An invalid Workday Common endpoint: '%s' was provided.", genericPath));
        }

        return String.format(WORKDAY_COMMON_API_URL_TEMPLATE, configuration.getHost(), configuration.getTenant(),
                pathString);
    }

}
