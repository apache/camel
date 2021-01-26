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
package org.apache.camel.component.huaweicloud.smn;

import java.util.List;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;

public class TestUtils {
    /**
     * get the request from wiremock serve events which was sent as part of templated publish notification
     * 
     * @param  serveEvents
     * @return
     */
    public static LoggedRequest retrieveTemplatedNotificationRequest(List<ServeEvent> serveEvents) {
        LoggedRequest loggedRequest = null;
        for (ServeEvent event : getAllServeEvents()) {
            if (event.getRequest().getBodyAsString().contains("\"tags\"")) {
                loggedRequest = event.getRequest();
                break;
            }
        }
        return loggedRequest;
    }

    /**
     * get the request from wiremock serve events which was sent as part of text notification
     * 
     * @param  serveEvents
     * @return
     */
    public static LoggedRequest retrieveTextNotificationRequest(List<ServeEvent> serveEvents) {
        LoggedRequest loggedRequest = null;
        for (ServeEvent event : getAllServeEvents()) {
            if (!event.getRequest().getBodyAsString().contains("\"tags\"")) {
                loggedRequest = event.getRequest();
                break;
            }
        }
        return loggedRequest;
    }
}
