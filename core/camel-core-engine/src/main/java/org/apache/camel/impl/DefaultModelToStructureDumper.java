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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.StringHelper;

@JdkService(ModelToStructureDumper.FACTORY)
public class DefaultModelToStructureDumper implements ModelToStructureDumper {

    @Override
    public List<ModelDumpLine> dumpStructure(CamelContext context, String routeId, boolean brief) throws Exception {
        List<ModelDumpLine> answer = new ArrayList<>();

        // lookup model and runtime route
        final Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
        final RouteDefinition def = model.getRouteDefinition(routeId);
        final Route route = context.getRoute(routeId);
        // dump in text format padded by level
        String scheme = def.getResource() != null ? def.getResource().getScheme() : "file";

        String loc
                = scheme + ":" + (route != null ? route.getSourceLocationShort() : LoggerHelper.getLineNumberLoggerName(def));
        answer.add(new ModelDumpLine(loc, "route", def.getRouteId(), 0, "route[" + def.getRouteId() + "]"));
        String uri;
        if (route != null) {
            uri = brief ? route.getEndpoint().getEndpointBaseUri() : route.getEndpoint().getEndpointUri();
        } else {
            uri = def.getInput().getEndpointUri();
            if (brief) {
                uri = StringHelper.before(uri, "?", uri);
            }
        }
        answer.add(new ModelDumpLine(loc, "from", routeId, 1, "from[" + uri + "]"));

        MBeanServer server = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            String jmxDomain = context.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
            // get all the processor mbeans and sort them accordingly to their index
            String prefix = context.getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName.getInstance(
                    jmxDomain + ":context=" + prefix + context.getManagementName() + ",type=processors,*");
            Set<ObjectName> names = server.queryNames(query, null);
            List<ManagedProcessorMBean> mps = new ArrayList<>();
            for (ObjectName on : names) {
                ManagedProcessorMBean processor = context.getManagementStrategy().getManagementAgent().newProxyClient(on,
                        ManagedProcessorMBean.class);
                // the processor must belong to this route
                if (def.getRouteId().equals(processor.getRouteId())) {
                    mps.add(processor);
                }
            }
            // sort by index
            mps.sort(new OrderProcessorMBeans());

            // dump in text format padded by level
            for (ManagedProcessorMBean processor : mps) {
                // include scheme in loc
                loc = scheme + ":" + processor.getSourceLocationShort();
                String kind = processor.getProcessorName();
                String id = processor.getProcessorId();
                int level = processor.getLevel() + 1;
                String code = brief ? processor.getProcessorName() : processor.getModelLabel();
                answer.add(new ModelDumpLine(loc, kind, id, level, code));
            }
        }

        return answer;
    }

    /**
     * Used for sorting the processor mbeans accordingly to their index.
     */
    private static final class OrderProcessorMBeans implements Comparator<ManagedProcessorMBean> {

        @Override
        public int compare(ManagedProcessorMBean o1, ManagedProcessorMBean o2) {
            return o1.getIndex().compareTo(o2.getIndex());
        }
    }

}
