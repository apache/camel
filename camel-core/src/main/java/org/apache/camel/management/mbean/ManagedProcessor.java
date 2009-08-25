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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */
@ManagedResource(description = "Managed Processor")
public class ManagedProcessor extends ManagedPerformanceCounter {

    private final CamelContext context;
    private final Processor processor;
    private final ProcessorDefinition definition;
    private final String routeId;
    private final String id;

    public ManagedProcessor(CamelContext context, Processor processor, ProcessorDefinition definition) {
        super(context.getManagementStrategy());
        this.context = context;
        this.processor = processor;
        this.definition = definition;
        this.routeId = doGetRouteId(definition);
        this.id = definition.idOrCreate(context.getNodeIdFactory());
    }

    public CamelContext getContext() {
        return context;
    }

    public Processor getProcessor() {
        return processor;
    }

    public ProcessorDefinition getDefinition() {
        return definition;
    }

    @ManagedAttribute(description = "Route id")
    public String getRouteId() {
        return routeId;
    }

    @ManagedAttribute(description = "id")
    public String getId() {
        return id;
    }

    private String doGetRouteId(ProcessorDefinition definition) {
        if (definition == null) {
            return null;
        }

        if (definition instanceof RouteDefinition) {
            return definition.getId();
        } else {
            return doGetRouteId(definition.getParent());
        }
    }

}
