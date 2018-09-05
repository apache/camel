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
package org.apache.camel.component.restlet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidParameterException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.util.jsse.SSLContextParameters;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultRestletHost implements RestletHost {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRestletHost.class);
    private final RestletHostOptions resletHostOptions;
    private Server server;

    public DefaultRestletHost(RestletHostOptions restletHostOptions) {
        this.resletHostOptions = restletHostOptions;
    }

    @Override
    public void configure(RestletEndpoint endpoint, Component component) {
        server = createServer(endpoint, component);
        component.getServers().add(server);

        // Add any Restlet server parameters that were included
        Series<Parameter> params = server.getContext().getParameters();

        if ("https".equals(endpoint.getProtocol())) {
            SSLContextParameters scp = endpoint.getSslContextParameters();
            if (endpoint.getSslContextParameters() == null) {
                throw new InvalidParameterException("Need to specify the SSLContextParameters option here!");
            }
            try {
                setupServerWithSSLContext(endpoint, params, scp);
            } catch (Exception e) {
                throw new IllegalStateException("Error configuring Restlet server SSL context", e);
            }
        }

        if (resletHostOptions.getControllerDaemon() != null) {
            params.add("controllerDaemon", resletHostOptions.getControllerDaemon().toString());
        }
        if (resletHostOptions.getControllerSleepTimeMs() != null) {
            params.add("controllerSleepTimeMs", resletHostOptions.getControllerSleepTimeMs().toString());
        }
        if (resletHostOptions.getInboundBufferSize() != null) {
            params.add("inboundBufferSize", resletHostOptions.getInboundBufferSize().toString());
        }
        if (resletHostOptions.getMaxConnectionsPerHost() != null) {
            params.add("maxConnectionsPerHost", resletHostOptions.getMaxConnectionsPerHost().toString());
        }
        if (resletHostOptions.getMaxQueued() != null) {
            params.add("maxQueued", resletHostOptions.getMaxQueued().toString());
        }
        if (resletHostOptions.getMaxThreads() != null) {
            params.add("maxThreads", resletHostOptions.getMaxThreads().toString());
        }
        if (resletHostOptions.getMaxTotalConnections() != null) {
            params.add("maxTotalConnections", resletHostOptions.getMaxTotalConnections().toString());
        }
        if (resletHostOptions.getMinThreads() != null) {
            params.add("minThreads", resletHostOptions.getMinThreads().toString());
        }
        if (resletHostOptions.getLowThreads() != null) {
            params.add("lowThreads", resletHostOptions.getLowThreads().toString());
        }
        if (resletHostOptions.getOutboundBufferSize() != null) {
            params.add("outboundBufferSize", resletHostOptions.getOutboundBufferSize().toString());
        }
        if (resletHostOptions.getPersistingConnections() != null) {
            params.add("persistingConnections", resletHostOptions.getPersistingConnections().toString());
        }
        if (resletHostOptions.getPipeliningConnections() != null) {
            params.add("pipeliningConnections", resletHostOptions.getPipeliningConnections().toString());
        }
        if (resletHostOptions.getThreadMaxIdleTimeMs() != null) {
            params.add("threadMaxIdleTimeMs", resletHostOptions.getThreadMaxIdleTimeMs().toString());
        }
        if (resletHostOptions.getUseForwardedForHeader() != null) {
            params.add("useForwardedForHeader", resletHostOptions.getUseForwardedForHeader().toString());
        }
        if (resletHostOptions.getReuseAddress() != null) {
            params.add("reuseAddress", resletHostOptions.getReuseAddress().toString());
        }

        LOG.debug("Setting parameters: {} to server: {}", params, server);
        server.getContext().setParameters(params);
    }

    @Override
    public void start() throws Exception {
        server.start();
    }

    @Override
    public void stop() throws Exception {
        server.stop();
    }

    private Server createServer(RestletEndpoint endpoint, Component component) {
        // Consider hostname if provided. This is useful when loopback interface is required for security reasons.
        if (endpoint.getHost() != null) {
            return new Server(component.getContext().createChildContext(), Protocol.valueOf(endpoint.getProtocol()), endpoint.getHost(), endpoint.getPort(), null);
        } else {
            return new Server(component.getContext().createChildContext(), Protocol.valueOf(endpoint.getProtocol()), endpoint.getPort());
        }
    }

    private void setupServerWithSSLContext(RestletEndpoint endpoint, Series<Parameter> params, SSLContextParameters scp) throws GeneralSecurityException, IOException {
        // set the SSLContext parameters
        params.add("sslContextFactory",
                "org.restlet.engine.ssl.DefaultSslContextFactory");

        SSLContext context = scp.createSSLContext(endpoint.getCamelContext());
        SSLEngine engine = context.createSSLEngine();

        params.add("enabledProtocols", String.join(" ", Arrays.asList(engine.getEnabledProtocols())));
        params.add("enabledCipherSuites", String.join(" ", Arrays.asList(engine.getEnabledCipherSuites())));

        if (scp.getSecureSocketProtocol() != null) {
            params.add("protocol", scp.getSecureSocketProtocol());
        }
        if (scp.getServerParameters() != null && scp.getServerParameters().getClientAuthentication() != null) {
            boolean b = !scp.getServerParameters().getClientAuthentication().equals("NONE");
            params.add("needClientAuthentication", String.valueOf(b));
        }
        if (scp.getKeyManagers() != null) {
            if (scp.getKeyManagers().getAlgorithm() != null) {
                params.add("keyManagerAlgorithm", scp.getKeyManagers().getAlgorithm());
            }
            if (scp.getKeyManagers().getKeyPassword() != null) {
                params.add("keyPassword", scp.getKeyManagers().getKeyPassword());
            }
            if (scp.getKeyManagers().getKeyStore().getResource() != null) {
                params.add("keyStorePath", scp.getKeyManagers().getKeyStore().getResource());
            }
            if (scp.getKeyManagers().getKeyStore().getPassword() != null) {
                params.add("keyStorePassword", scp.getKeyManagers().getKeyStore().getPassword());
            }
            if (scp.getKeyManagers().getKeyStore().getType() != null) {
                params.add("keyStoreType", scp.getKeyManagers().getKeyStore().getType());
            }
        }

        if (scp.getTrustManagers() != null) {
            if (scp.getTrustManagers().getAlgorithm() != null) {
                params.add("trustManagerAlgorithm", scp.getKeyManagers().getAlgorithm());
            }
            if (scp.getTrustManagers().getKeyStore().getResource() != null) {
                params.add("trustStorePath", scp.getTrustManagers().getKeyStore().getResource());
            }
            if (scp.getTrustManagers().getKeyStore().getPassword() != null) {
                params.add("trustStorePassword", scp.getTrustManagers().getKeyStore().getPassword());
            }
            if (scp.getTrustManagers().getKeyStore().getType() != null) {
                params.add("trustStoreType", scp.getTrustManagers().getKeyStore().getType());
            }
        }
    }
}
