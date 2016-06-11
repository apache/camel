/**
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
package org.apache.camel.management.mbean;

import java.util.List;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.Endpoint;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedEndpointMBean;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed Endpoint")
public class ManagedEndpoint implements ManagedInstance, ManagedEndpointMBean {
    private final Endpoint endpoint;

    public ManagedEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void init(ManagementStrategy strategy) {
        // noop
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public String getCamelId() {
        return endpoint.getCamelContext().getName();
    }

    @Override
    public String getCamelManagementName() {
        return endpoint.getCamelContext().getManagementName();
    }

    @Override
    public String getEndpointUri() {
        return endpoint.getEndpointUri();
    }

    @Override
    public boolean isSingleton() {
        return endpoint.isSingleton();
    }

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (endpoint instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) endpoint).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @Override
    public String informationJson() {
        return endpoint.getCamelContext().explainEndpointJson(getEndpointUri(), true);
    }

    @Override
    public TabularData explain(boolean allOptions) {
        try {
            String json = endpoint.getCamelContext().explainEndpointJson(getEndpointUri(), allOptions);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("properties", json, true);

            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.explainEndpointTabularType());

            for (Map<String, String> row : rows) {
                String name = row.get("name");
                String kind = row.get("kind");
                String group = row.get("group") != null ? row.get("group") : "";
                String label = row.get("label") != null ? row.get("label") : "";
                String type = row.get("type");
                String javaType = row.get("javaType");
                String deprecated = row.get("deprecated") != null ? row.get("deprecated") : "";
                String secret = row.get("secret") != null ? row.get("secret") : "";
                String value = row.get("value") != null ? row.get("value") : "";
                String defaultValue = row.get("defaultValue") != null ? row.get("defaultValue") : "";
                String description = row.get("description") != null ? row.get("description") : "";

                CompositeType ct = CamelOpenMBeanTypes.explainEndpointsCompositeType();
                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"option", "kind", "group", "label", "type", "java type", "deprecated", "secret", "value", "default value", "description"},
                        new Object[]{name, kind, group, label, type, javaType, deprecated, secret, value, defaultValue, description});
                answer.put(data);
            }

            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public Endpoint getInstance() {
        return endpoint;
    }

}
