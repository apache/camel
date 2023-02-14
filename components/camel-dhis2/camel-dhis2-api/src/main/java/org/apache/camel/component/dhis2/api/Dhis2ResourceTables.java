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
