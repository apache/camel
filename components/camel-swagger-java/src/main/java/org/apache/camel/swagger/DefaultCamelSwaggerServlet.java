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
package org.apache.camel.swagger;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.util.CamelVersionHelper;

public class DefaultCamelSwaggerServlet extends RestSwaggerApiDeclarationServlet {

    @Override
    public List<RestDefinition> getRestDefinitions(String camelId) throws Exception {
        ObjectName found = null;

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = server.queryNames(new ObjectName("org.apache.camel:type=context,*"), null);
        for (ObjectName on : names) {
            String id = on.getKeyProperty("name");
            if (id.startsWith("\"") && id.endsWith("\"")) {
                id = id.substring(1, id.length() - 1);
            }
            if (camelId == null || camelId.equals(id)) {
                // filter out older Camel versions as this requires Camel 2.15 or better
                String version = (String) server.getAttribute(on, "CamelVersion");
                if (CamelVersionHelper.isGE("2.15.0", version)) {
                    found = on;
                }
            }
        }

        if (found != null) {
            String xml = (String) server.invoke(found, "dumpRestsAsXml", null, null);
            if (xml != null) {
                RestsDefinition rests = ModelHelper.createModelFromXml(null, xml, RestsDefinition.class);
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

            // filter out older Camel versions as this requires Camel 2.15 or better
            String version = (String) server.getAttribute(on, "CamelVersion");
            if (CamelVersionHelper.isGE("2.15.0", version)) {
                answer.add(id);
            }
        }
        return answer;
    }
}
