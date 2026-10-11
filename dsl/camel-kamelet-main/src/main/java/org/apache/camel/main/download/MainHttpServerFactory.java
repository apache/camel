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
package org.apache.camel.main.download;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConstants;
import org.apache.camel.main.MainHelper;
import org.apache.camel.main.util.CamelJBangSettingsHelper;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.StringHelper.startsWithIgnoreCase;

public class MainHttpServerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MainHttpServerFactory.class);

    private static final String PREFIX_SERVER = "camel.server.";

    public static MainHttpServer setupHttpServer(CamelContext camelContext, boolean silent) {
        // if we only use management then there is no main server already
        MainHttpServer server = camelContext.hasService(MainHttpServer.class);
        ManagementHttpServer managementHttpServer = camelContext.hasService(ManagementHttpServer.class);
        // but if none has already been created, and we are using platform-http, then we need an embedded default http server
        if (server == null && managementHttpServer == null) {
            // set up a default http server on configured port if not already done
            HttpServerConfigurationProperties config = new HttpServerConfigurationProperties(null);
            // apply the camel.server.* settings (port, host, path ...) as camel-main does when the server is enabled
            configureHttpServer(camelContext, config);
            String port = CamelJBangSettingsHelper.readSettings("camel.server.port");
            if (port != null) {
                config.setPort(CamelContextHelper.parseInt(camelContext, port));
            } else {
                CamelJBangSettingsHelper.writeSettingsIfNotExists("camel.server.port",
                        String.valueOf(config.getPort()));
            }
            if (!silent) {
                try {
                    // enable http server if not silent
                    org.apache.camel.main.MainHttpServerFactory factory = resolveMainHttpServerFactory(camelContext);
                    Service httpServer = factory.newHttpServer(camelContext, config);
                    camelContext.addService(httpServer, true, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return server;
    }

    /**
     * Binds the camel.server.* properties (application.properties, --properties, initial and override properties, OS
     * environment variables and JVM system properties) on the configuration of the HTTP server that is started
     * implicitly for platform-http, as camel-main only applies them to the server it starts itself when
     * camel.server.enabled=true.
     */
    static void configureHttpServer(CamelContext camelContext, HttpServerConfigurationProperties config) {
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> startsWithIgnoreCase(name, PREFIX_SERVER), MainHelper::optionKey);
        // OS environment variables and then JVM system properties override, as in camel-main
        Properties propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { PREFIX_SERVER });
        for (String key : propENV.stringPropertyNames()) {
            prop.put("ENV", MainHelper.optionKey(key), propENV.getProperty(key));
        }
        Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { PREFIX_SERVER });
        for (String key : propJVM.stringPropertyNames()) {
            prop.put("SYS", MainHelper.optionKey(key), propJVM.getProperty(key));
        }

        // all the keys start with camel.server. as every source is filtered on it
        OrderedLocationProperties options = new OrderedLocationProperties();
        for (Object k : prop.keySet()) {
            String option = k.toString().substring(PREFIX_SERVER.length());
            // enabled has no meaning here, as the server is started because platform-http is in use
            if (!"enabled".equalsIgnoreCase(option)) {
                options.put(prop.getLocation(k), option, prop.get(k));
            }
        }
        if (!options.isEmpty()) {
            LOG.debug("Configuring embedded HTTP server for platform-http from properties: {}", options.size());
            MainHelper.setPropertiesOnTarget(camelContext, config, options, PREFIX_SERVER, false, true,
                    new OrderedLocationProperties());
        }
    }

    private static org.apache.camel.main.MainHttpServerFactory resolveMainHttpServerFactory(CamelContext camelContext)
            throws Exception {
        // lookup in service registry first
        org.apache.camel.main.MainHttpServerFactory answer
                = camelContext.getRegistry().findSingleByType(org.apache.camel.main.MainHttpServerFactory.class);
        if (answer == null) {
            answer = camelContext.getCamelContextExtension().getBootstrapFactoryFinder()
                    .newInstance(MainConstants.PLATFORM_HTTP_SERVER, org.apache.camel.main.MainHttpServerFactory.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot find MainHttpServerFactory on classpath. Add camel-platform-http-main to classpath."));
        }
        return answer;
    }

}
