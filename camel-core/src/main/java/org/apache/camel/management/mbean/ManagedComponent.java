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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.Component;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedComponentMBean;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
@ManagedResource(description = "Managed Component")
public class ManagedComponent implements ManagedInstance, ManagedComponentMBean {
    private final Component component;
    private final String name;

    public ManagedComponent(String name, Component component) {
        this.name = name;
        this.component = component;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public Component getComponent() {
        return component;
    }

    public String getComponentName() {
        return name;
    }

    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (component instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) component).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    public String getCamelId() {
        return component.getCamelContext().getName();
    }

    public String getCamelManagementName() {
        return component.getCamelContext().getManagementName();
    }

    public Object getInstance() {
        return component;
    }

    public String informationJson() {
        try {
            // a component may have been given a different name, so resolve its default name by its java type
            // as we can find the component json information from the default component name
            String defaultName = component.getCamelContext().resolveComponentDefaultName(component.getClass().getName());
            String target = defaultName != null ? defaultName : name;
            return component.getCamelContext().getComponentParameterJsonSchema(target);
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public TabularData explain(boolean allOptions) {
        try {
            // a component may have been given a different name, so resolve its default name by its java type
            // as we can find the component json information from the default component name
            String defaultName = component.getCamelContext().resolveComponentDefaultName(component.getClass().getName());
            String target = defaultName != null ? defaultName : name;
            String json = component.getCamelContext().explainComponentJson(target, allOptions);

            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("componentProperties", json, true);

            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.explainComponentTabularType());

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

                CompositeType ct = CamelOpenMBeanTypes.explainComponentCompositeType();
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
    public boolean isVerifySupported() {
        return component instanceof VerifiableComponent;
    }

    @Override
    public ComponentVerifier.Result verify(String scope, Map<String, String> options) {
        try {
            ComponentVerifier.Scope scopeEnum = ComponentVerifier.Scope.fromString(scope);

            if (component instanceof VerifiableComponent) {
                return ((VerifiableComponent) component).getVerifier().verify(scopeEnum, CastUtils.cast(options));
            } else {
                return ResultBuilder.unsupported().build();
            }
        } catch (IllegalArgumentException e) {
            return ResultBuilder.withStatus(ComponentVerifier.Result.Status.UNSUPPORTED)
                .error(ResultErrorBuilder.withUnsupportedScope(scope).build())
                .build();
        }
    }
}
