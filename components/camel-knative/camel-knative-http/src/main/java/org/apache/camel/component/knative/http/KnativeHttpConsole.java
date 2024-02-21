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
package org.apache.camel.component.knative.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;

@DevConsole("knative")
public class KnativeHttpConsole extends AbstractDevConsole {

    public KnativeHttpConsole() {
        super("camel", "knative", "Knative", "Knative HTTP Service");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        // find if we use MainHttpServer and get its configuration for url
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            String host = null;
            int port = -1;
            String path = null;
            try {
                Service service = getCamelContext().hasService(s -> s.getClass().getSimpleName().equals("MainHttpServer"));
                if (service != null) {
                    MBeanServer mBeanServer = getCamelContext().getManagementStrategy().getManagementAgent().getMBeanServer();
                    ObjectName on = getCamelContext().getManagementStrategy().getManagementObjectNameStrategy()
                            .getObjectNameForService(getCamelContext(), service);
                    host = (String) mBeanServer.getAttribute(on, "Host");
                    port = (int) mBeanServer.getAttribute(on, "Port");
                    path = (String) mBeanServer.getAttribute(on, "Path");
                }
            } catch (Exception e) {
                // ignore
            }

            List<Consumer> list = getCamelContext().getRoutes()
                    .stream().map(Route::getConsumer)
                    .filter(c -> KnativeHttpConsumer.class.getName().equals(c.getClass().getName()))
                    .toList();

            for (Consumer c : list) {
                KnativeHttpConsumer knc = (KnativeHttpConsumer) c;
                if (host != null) {
                    String p = path != null ? path + "/" + knc.getPath() : knc.getPath();
                    // remove leading slashes
                    p = StringHelper.removeStartingCharacters(p, '/');
                    sb.append(String.format("    %s://%s:%d/%s\n", "http", host, port, p));
                } else {
                    sb.append(String.format("    %s://%s\n", "http", knc.getPath()));
                }

            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            String host = null;
            int port = -1;
            String path = null;
            try {
                Service service = getCamelContext().hasService(s -> s.getClass().getSimpleName().equals("MainHttpServer"));
                if (service != null) {
                    MBeanServer mBeanServer = getCamelContext().getManagementStrategy().getManagementAgent().getMBeanServer();
                    ObjectName on = getCamelContext().getManagementStrategy().getManagementObjectNameStrategy()
                            .getObjectNameForService(getCamelContext(), service);
                    host = (String) mBeanServer.getAttribute(on, "Host");
                    port = (int) mBeanServer.getAttribute(on, "Port");
                    path = (String) mBeanServer.getAttribute(on, "Path");
                }
            } catch (Exception e) {
                // ignore
            }
            List<Consumer> list = getCamelContext().getRoutes()
                    .stream().map(Route::getConsumer)
                    .filter(c -> KnativeHttpConsumer.class.getName().equals(c.getClass().getName()))
                    .toList();

            List<JsonObject> arr = new ArrayList<>();
            for (Consumer c : list) {
                KnativeHttpConsumer knc = (KnativeHttpConsumer) c;

                JsonObject jo = new JsonObject();
                jo.put("protocol", "http");
                if (host != null) {
                    jo.put("host", host);
                }
                if (port != -1) {
                    jo.put("port", port);
                }
                String p = path != null ? path + "/" + knc.getPath() : knc.getPath();
                // remove leading slashes
                p = StringHelper.removeStartingCharacters(p, '/');
                jo.put("path", p);
                arr.add(jo);
            }
            if (!arr.isEmpty()) {
                root.put("consumers", arr);
            }
        }

        return root;
    }

}
