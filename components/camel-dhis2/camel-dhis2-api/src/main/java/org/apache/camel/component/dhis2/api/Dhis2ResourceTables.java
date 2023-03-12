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
package org.apache.camel.component.dhis2.api;

import java.util.Map;
import java.util.Objects;

import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.operation.PostOperation;

public class Dhis2ResourceTables {
    private final Dhis2Client dhis2Client;

    public Dhis2ResourceTables(Dhis2Client dhis2Client) {
        this.dhis2Client = dhis2Client;
    }

    public void analytics(Boolean skipAggregate, Boolean skipEvents, Integer lastYears, Integer interval) {
        PostOperation postOperation = dhis2Client.post("resourceTables/analytics");
        if (skipEvents != null) {
            postOperation.withParameter("skipEvents", String.valueOf(skipEvents));
        }
        if (skipEvents != null) {
            postOperation.withParameter("skipAggregate", String.valueOf(skipAggregate));
        }
        if (lastYears != null) {
            postOperation.withParameter("lastYears", String.valueOf(lastYears));
        }

        Map<String, Object> webMessage = postOperation.transfer().returnAs(Map.class);
        String taskId = (String) ((Map<String, Object>) webMessage.get("response")).get("id");

        Map<String, Object> notification = null;
        while (notification == null || !(Boolean) notification.get("completed")) {
            try {
                Thread.sleep(Objects.requireNonNullElse(interval, 30000));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Iterable<Map> notifications = dhis2Client.get("system/tasks/ANALYTICS_TABLE/{taskId}",
                    taskId).withoutPaging().transfer().returnAs(Map.class);
            if (notifications.iterator().hasNext()) {
                notification = notifications.iterator().next();
                if (notification.get("level").equals("ERROR")) {
                    throw new RuntimeException("Analytics failed => " + notification);
                }
            }
        }
    }

}
