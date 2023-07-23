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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.StepDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;

@ManagedResource(description = "Managed Processor")
public class ManagedProcessor extends ManagedPerformanceCounter implements ManagedInstance, ManagedProcessorMBean {

    private final CamelContext context;
    private final Processor processor;
    private final ProcessorDefinition<?> definition;
    private final String id;
    private final int nodeLevel;
    private final String stepId;
    private Route route;
    private String sourceLocation;

    public ManagedProcessor(CamelContext context, Processor processor, ProcessorDefinition<?> definition) {
        this.context = context;
        this.processor = processor;
        this.definition = definition;
        this.nodeLevel = ProcessorDefinitionHelper.getNodeLevel(definition);
        this.id = definition.idOrCreate(context.getCamelContextExtension().getContextPlugin(NodeIdFactory.class));
        StepDefinition step;
        if (definition instanceof StepDefinition) {
            step = (StepDefinition) definition;
        } else {
            step = ProcessorDefinitionHelper.findFirstParentOfType(StepDefinition.class, definition, true);
        }
        this.stepId = step != null
                ? step.idOrCreate(context.getCamelContextExtension().getContextPlugin(NodeIdFactory.class)) : null;
        this.sourceLocation = definition.getLocation();
        if (sourceLocation == null) {
            RouteDefinition rd = ProcessorDefinitionHelper.getRoute(definition);
            sourceLocation = rd != null ? rd.getLocation() : null;
        }
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

    @Override
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

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public Integer getIndex() {
        return definition.getIndex();
    }

    @Override
    public int getLevel() {
        return nodeLevel;
    }

    @Override
    public String getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public Integer getSourceLineNumber() {
        int line = definition.getLineNumber();
        return line >= 0 ? line : null;
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return false;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (processor instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) processor).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @Override
    public String getCamelId() {
        return context.getName();
    }

    @Override
    public String getCamelManagementName() {
        return context.getManagementName();
    }

    @Override
    public String getRouteId() {
        if (route != null) {
            return route.getId();
        } else if (processor instanceof RouteIdAware) {
            return ((RouteIdAware) processor).getRouteId();
        }
        return null;
    }

    @Override
    public String getProcessorId() {
        return id;
    }

    @Override
    public String getProcessorName() {
        return definition.getShortName();
    }

    @Override
    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        ServiceHelper.startService(getProcessor());
    }

    @Override
    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        ServiceHelper.stopService(getProcessor());
    }

    @Override
    public String dumpProcessorAsXml() throws Exception {
        return PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, definition);
    }
}
