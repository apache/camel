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

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConstants;
import org.apache.camel.main.util.CamelJBangSettingsHelper;

public class MainHttpServerFactory {

    public static MainHttpServer setupHttpServer(CamelContext camelContext, boolean silent) {
        // set up a default http server on configured port if not already done
        MainHttpServer server = camelContext.hasService(MainHttpServer.class);
        if (server == null) {
            // need to capture that we use a http-server
            HttpServerConfigurationProperties config = new HttpServerConfigurationProperties(null);
            CamelJBangSettingsHelper.writeSettingsIfNotExists("camel.jbang.platform-http.port",
                    String.valueOf(config.getPort()));
            if (!silent) {
                try {
                    // enable http server if not silent
                    org.apache.camel.main.MainHttpServerFactory factory = resolveMainHttpServerFactory(camelContext);
                    Service httpServer = factory.newHttpServer(config);
                    camelContext.addService(httpServer, true, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return server;
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
