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
package org.apache.camel.component.platform.http.plugin;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.http.HttpRequestHandler;
import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.server.core.restrictor.DenyAllRestrictor;
import org.jolokia.server.core.restrictor.RestrictorFactory;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.service.jmx.LocalRequestHandler;
import org.jolokia.service.serializer.JolokiaSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(DefaultJolokiaPlatformHttpPlugin.NAME)
public class DefaultJolokiaPlatformHttpPlugin extends ServiceSupport implements JolokiaPlatformHttpPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJolokiaPlatformHttpPlugin.class);

    private final JolokiaServiceManager serviceManager;

    private HttpRequestHandler requestHandler;

    private final LogHandler jolokiaLogHandler;

    private CamelContext camelContext;

    public DefaultJolokiaPlatformHttpPlugin() {
        var config = new StaticConfiguration(ConfigKey.AGENT_ID, NetworkUtil.getAgentId(hashCode(), "vertx"));
        jolokiaLogHandler = new JolokiaLogHandler(LOG);
        var restrictor = createRestrictor(NetworkUtil.replaceExpression(config.getConfig(ConfigKey.POLICY_LOCATION)));

        serviceManager = JolokiaServiceManagerFactory.createJolokiaServiceManager(
                config,
                jolokiaLogHandler,
                restrictor);
        serviceManager.addService(new JolokiaSerializer());
        serviceManager.addService(new LocalRequestHandler(1));

        LOG.info("Creating DefaultJolokiaPlatformHttpPlugin with restrictor {}", restrictor);
    }

    @Override
    public void doStart() {
        var jolokiaContext = serviceManager.start();
        requestHandler = new HttpRequestHandler(jolokiaContext);
    }

    @Override
    public void doStop() {
        serviceManager.stop();
    }

    @Override
    public HttpRequestHandler getRequestHandler() {
        return requestHandler;
    }

    private Restrictor createRestrictor(String pLocation) {
        try {
            var restrictor = RestrictorFactory.lookupPolicyRestrictor(pLocation);
            if (restrictor != null) {
                jolokiaLogHandler.info("Using access restrictor " + pLocation);
                return restrictor;
            } else {
                jolokiaLogHandler.info("No access restrictor found at " + pLocation + ", access to all MBeans is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            jolokiaLogHandler.error("Error while accessing access restrictor at " + pLocation +
                                    ". Denying all access to MBeans for security reasons. Exception: " + e,
                    e);
            return new DenyAllRestrictor();
        }
    }

    @Override
    public String getId() {
        return NAME;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    private record JolokiaLogHandler(Logger log) implements LogHandler {

        @Override
        public void debug(String message) {
            log.debug(message);
        }

        @Override
        public void info(String message) {
            log.info(message);
        }

        @Override
        public void error(String message, Throwable t) {
            log.error(message, t);
        }

        @Override
        public boolean isDebug() {
            return log.isDebugEnabled();
        }
    }

}
