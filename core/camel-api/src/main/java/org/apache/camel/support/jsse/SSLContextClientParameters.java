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
package org.apache.camel.support.jsse;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration model for client side JSSE options.
 */
public class SSLContextClientParameters extends BaseSSLContextParameters {

    private static final Logger LOG = LoggerFactory.getLogger(SSLContextClientParameters.class);

    /**
     * SNI hostnames to be used for SSL (Server Name Indicator)
     */
    private final List<String> sniHostNames = new ArrayList<>();

    /**
     * The endpoint identification algorithm, such as HTTPS or LDAPS
     */
    private @Nullable String endpointIdentificationAlgorithm;

    public void addAllSniHostNames(List<String> sniHostNames) {
        Objects.requireNonNull(sniHostNames, "sniHostNames");
        this.sniHostNames.addAll(sniHostNames);
    }

    public void setSniHostName(String sniHostName) {
        Objects.requireNonNull(sniHostName, "sniHostName");
        this.sniHostNames.add(sniHostName);
    }

    /**
     * Returns the endpoint identification algorithm that client sockets use to check that the certificate of the server
     * matches the host they connect to, or {@code null} if it is not set.
     */
    public @Nullable String getEndpointIdentificationAlgorithm() {
        return endpointIdentificationAlgorithm;
    }

    /**
     * Sets the endpoint identification algorithm, such as {@code HTTPS} or {@code LDAPS}, that the client uses to check
     * that the certificate of the server matches the host it connects to (host name verification). It is applied to the
     * {@code SSLSocket} instances that the {@code SSLSocketFactory} of the context creates, which are client sockets.
     * Unset by default, which keeps the behavior of the JDK: no host name verification.
     * <p>
     * The JDK checks the host that the socket was created with, so a socket that is created without one, and connected
     * afterwards, fails the handshake. An {@code SSLEngine} starts in server mode and is switched to client mode by the
     * component, so this setting is not applied to engines: a component that uses an {@code SSLEngine} applies it to
     * the {@code SSLParameters} of the engine once it is in client mode, as for its own host name verification option.
     *
     * @param endpointIdentificationAlgorithm the algorithm, or {@code null} to not verify the host name
     */
    public void setEndpointIdentificationAlgorithm(@Nullable String endpointIdentificationAlgorithm) {
        this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
    }

    @Override
    protected @Nullable String getSocketEndpointIdentificationAlgorithm() {
        // the algorithm may use a property placeholder
        return parsePropertyValue(endpointIdentificationAlgorithm);
    }

    @Override
    protected List<SNIServerName> getSNIHostNames() {
        // the host names may use property placeholders
        List<SNIServerName> answer = new ArrayList<>(sniHostNames.size());
        for (String name : sniHostNames) {
            answer.add(new SNIHostName(parsePropertyValue(name)));
        }
        return answer;
    }

    @Override
    protected boolean getAllowPassthrough() {
        return true;
    }

    @Override
    protected void configureSSLContext(SSLContext context) throws GeneralSecurityException {
        LOG.trace("Configuring client-side SSLContext parameters on SSLContext [{}]...", context);
        if (this.getSessionTimeout() != null) {
            LOG.info("Configuring client-side SSLContext session timeout on SSLContext [{}] to [{}].", context,
                    this.getSessionTimeout());
            this.configureSessionContext(context.getClientSessionContext(), this.getSessionTimeout());
        }
        LOG.trace("Configured client-side SSLContext parameters on SSLContext [{}].", context);
    }

    /**
     * This implementation only configures the SNI host names, as the enabled cipher suites and protocols are not client
     * and server side specific in an {@code SSLEngine}. Consequently, overriding them here would be a bit odd as the
     * client side specific configuration shouldn't really override a shared client/server configuration option.
     */
    @Override
    protected List<Configurer<SSLEngine>> getSSLEngineConfigurers(SSLContext context) {
        final List<SNIServerName> names = getSNIHostNames();
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        // the server names are only used by an engine in client mode
        Configurer<SSLEngine> sniConfigurer = engine -> {
            SSLParameters params = engine.getSSLParameters();
            params.setServerNames(names);
            engine.setSSLParameters(params);
            return engine;
        };
        return Collections.singletonList(sniConfigurer);
    }

    /**
     * This class has no bearing on {@code SSLServerSocketFactory} instances and therefore provides no configurers for
     * that purpose.
     */
    @Override
    protected List<Configurer<SSLServerSocketFactory>> getSSLServerSocketFactoryConfigurers(SSLContext context) {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SSLContextClientParameters[getCipherSuites()=");
        builder.append(getCipherSuites());
        builder.append(", getCipherSuitesFilter()=");
        builder.append(getCipherSuitesFilter());
        builder.append(", getSecureSocketProtocols()=");
        builder.append(getSecureSocketProtocols());
        builder.append(", getSecureSocketProtocolsFilter()=");
        builder.append(getSecureSocketProtocolsFilter());
        builder.append(", getSessionTimeout()=");
        builder.append(getSessionTimeout());
        builder.append(", getEndpointIdentificationAlgorithm()=");
        builder.append(getEndpointIdentificationAlgorithm());
        builder.append("]");
        return builder.toString();
    }
}
