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
package org.apache.camel.openapi.jmx;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.openapi.RestDefinitionsResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.xml.in.ModelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.openapi.RestDefinitionsResolver.JMX_REST_DEFINITION_RESOLVER;

/**
 * Resolves from JMX.
 */
@JdkService(JMX_REST_DEFINITION_RESOLVER)
public class JmxRestDefinitionsResolver implements RestDefinitionsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JmxRestDefinitionsResolver.class);

    @Override
    public List<RestDefinition> getRestDefinitions(CamelContext camelContext, String camelId) throws Exception {
        ObjectName found = null;

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = server.queryNames(new ObjectName("org.apache.camel:type=context,*"), null);
        for (ObjectName on : names) {
            String id = on.getKeyProperty("name");
            if (id.startsWith("\"") && id.endsWith("\"")) {
                id = id.substring(1, id.length() - 1);
            }
            if (camelId == null || camelId.equals(id)) {
                found = on;
            }
        }

        if (found != null) {
            String xml = (String) server.invoke(found, "dumpRestsAsXml", new Object[] { true },
                    new String[] { "boolean" });
            if (xml != null) {
                LOG.debug("DumpRestAsXml:\n{}", xml);

                Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource("mem:" + xml);
                RestsDefinition rests = new ModelParser(resource).parseRestsDefinition().orElse(null);
                if (rests != null) {
                    return rests.getRests();
                }
            }
        }

        return null;
    }

    @Override
    public List<String> findCamelContexts() throws Exception {
        List<String> answer = new ArrayList<>();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = server.queryNames(new ObjectName("*:type=context,*"), null);
        for (ObjectName on : names) {

            String id = on.getKeyProperty("name");
            if (id.startsWith("\"") && id.endsWith("\"")) {
                id = id.substring(1, id.length() - 1);
            }
            answer.add(id);
        }
        return answer;
    }

}
