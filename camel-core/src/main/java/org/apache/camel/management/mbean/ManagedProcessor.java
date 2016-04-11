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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * @version 
 */
@ManagedResource(description = "Managed Processor")
public class ManagedProcessor extends ManagedPerformanceCounter implements ManagedInstance, ManagedProcessorMBean {

    private final CamelContext context;
    private final Processor processor;
    private final ProcessorDefinition<?> definition;
    private final String id;
    private Route route;

    public ManagedProcessor(CamelContext context, Processor processor, ProcessorDefinition<?> definition) {
        this.context = context;
        this.processor = processor;
        this.definition = definition;
        this.id = definition.idOrCreate(context.getNodeIdFactory());
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean enabled = context.getManagementStrategy().getManagementAgent().getStatisticsLevel().isDefaultOrExtended();
        setStatisticsEnabled(enabled);
    }

    public CamelContext getContext() {
        return context;
    }

    public Object getInstance() {
        return processor;
    }

    public Processor getProcessor() {
        return processor;
    }

    public ProcessorDefinition<?> getDefinition() {
        return definition;
    }

    public String getId() {
        return id;
    }

    public Integer getIndex() {
        return definition.getIndex();
    }

    public Boolean getSupportExtendedInformation() {
        return false;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (processor instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) processor).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    public String getCamelId() {
        return context.getName();
    }

    public String getCamelManagementName() {
        return context.getManagementName();
    }

    public String getRouteId() {
        if (route != null) {
            return route.getId();
        }
        return null;
    }

    public String getProcessorId() {
        return id;
    }

    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        ServiceHelper.startService(getProcessor());
    }

    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        ServiceHelper.stopService(getProcessor());
    }

    public String informationJson() {
        return context.explainEipJson(id, true);
    }

    public TabularData explain(boolean allOptions) {
        try {
            String json = context.explainEipJson(id, allOptions);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("properties", json, true);

            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.explainEipTabularType());

            for (Map<String, String> row : rows) {
                String name = row.get("name");
                String kind = row.get("kind");
                String label = row.get("label") != null ? row.get("label") : "";
                String type = row.get("type");
                String javaType = row.get("javaType");
                String deprecated = row.get("deprecated") != null ? row.get("deprecated") : "";
                String value = row.get("value") != null ? row.get("value") : "";
                String defaultValue = row.get("defaultValue") != null ? row.get("defaultValue") : "";
                String description = row.get("description") != null ? row.get("description") : "";

                CompositeType ct = CamelOpenMBeanTypes.explainEipsCompositeType();
                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"option", "kind", "label", "type", "java type", "deprecated", "value", "default value", "description"},
                        new Object[]{name, kind, label, type, javaType, deprecated, value, defaultValue, description});
                answer.put(data);
            }

            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public String dumpProcessorAsXml() throws Exception {
        return ModelHelper.dumpModelAsXml(context, definition);
    }
}
