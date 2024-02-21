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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeMBeanException;

import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.Arguments;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;
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
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("platform-http/" + JolokiaPlatformHttpPlugin.NAME)
public class DefaultJolokiaPlatformHttpPlugin extends ServiceSupport implements JolokiaPlatformHttpPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJolokiaPlatformHttpPlugin.class);

    private CamelContext camelContext;
    private final JolokiaServiceManager serviceManager;
    private final LogHandler jolokiaLogHandler;
    private HttpRequestHandler requestHandler;
    private Handler<RoutingContext> handler;

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

        LOG.info("Creating DefaultJolokiaPlatformHttpPlugin with Restrictor: {}", restrictor);
    }

    @Override
    public void doStart() {
        var jolokiaContext = serviceManager.start();
        requestHandler = new HttpRequestHandler(jolokiaContext);
        handler = createVertxHandler();
    }

    @Override
    public void doStop() {
        if (serviceManager != null) {
            serviceManager.stop();
        }
    }

    @Override
    public Object getHandler() {
        return handler;
    }

    public HttpRequestHandler getJolokiaRequestHandler() {
        return requestHandler;
    }

    private Restrictor createRestrictor(String pLocation) {
        try {
            var restrictor = RestrictorFactory.lookupPolicyRestrictor(pLocation);
            if (restrictor != null) {
                jolokiaLogHandler.info("Using access restrictor: " + pLocation);
                return restrictor;
            } else {
                jolokiaLogHandler.info("No access restrictor found at: " + pLocation + ", access to all MBeans is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            jolokiaLogHandler.error("Error while accessing access restrictor: at " + pLocation +
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

    private Handler<RoutingContext> createVertxHandler() {
        return routingContext -> {
            HttpServerRequest req = routingContext.request();
            String remainingPath = Utils.pathOffset(req.path(), routingContext);

            JSONAware json = null;
            try {
                requestHandler.checkAccess(req.remoteAddress().host(), req.remoteAddress().host(), getOriginOrReferer(req));
                if (req.method() == HttpMethod.GET) {
                    json = requestHandler.handleGetRequest(req.uri(), remainingPath, getParams(req.params()));
                } else {
                    Arguments.require(routingContext.body() != null, "Missing body");
                    InputStream inputStream = new ByteBufInputStream(routingContext.body().buffer().getByteBuf());
                    json = requestHandler.handlePostRequest(req.uri(), inputStream, StandardCharsets.UTF_8.name(),
                            getParams(req.params()));
                }
            } catch (Throwable exp) {
                json = requestHandler.handleThrowable(
                        exp instanceof RuntimeMBeanException ? ((RuntimeMBeanException) exp).getTargetException() : exp);
            } finally {
                if (json == null)
                    json = requestHandler.handleThrowable(new Exception("Internal error while handling an exception"));

                routingContext.response()
                        .setStatusCode(getStatusCode(json))
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end(json.toJSONString());
            }
        };
    }

    private Map<String, String[]> getParams(MultiMap params) {
        Map<String, String[]> response = new HashMap<>();
        for (String name : params.names()) {
            response.put(name, params.getAll(name).toArray(new String[0]));
        }
        return response;
    }

    private String getOriginOrReferer(HttpServerRequest req) {
        String origin = req.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            origin = req.getHeader(HttpHeaders.REFERER);
        }
        return origin != null ? origin.replaceAll("[\\n\\r]*", "") : null;
    }

    private int getStatusCode(JSONAware json) {
        if (json instanceof JSONObject && ((JSONObject) json).get("status") instanceof Integer) {
            return (Integer) ((JSONObject) json).get("status");
        }
        return 200;
    }

}
