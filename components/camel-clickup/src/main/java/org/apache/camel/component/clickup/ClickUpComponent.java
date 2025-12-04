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

package org.apache.camel.component.clickup;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.component.clickup.model.Events;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("clickup")
public class ClickUpComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ClickUpConfiguration configuration = new ClickUpConfiguration();

        if (remaining.endsWith("/")) {
            remaining = remaining.substring(0, remaining.length() - 1);
        }
        Long workspaceId = Long.parseLong(remaining);
        configuration.setWorkspaceId(workspaceId);

        ClickUpEndpoint endpoint = new ClickUpEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAuthorizationToken())) {
            throw new IllegalArgumentException("AuthorizationToken must be configured for clickup: " + uri);
        }

        if (!Events.areAllEventsSupported(endpoint.getConfiguration().getEvents())) {
            Set<String> unsupportedEvents =
                    Events.computeUnsupportedEvents(endpoint.getConfiguration().getEvents());

            throw new IllegalArgumentException("The following events are not yet supported: " + unsupportedEvents);
        }

        return endpoint;
    }
}
