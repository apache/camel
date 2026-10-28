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
package org.apache.camel.component.mina;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;

/**
 * The {@link SslFilter} of a connection that Camel opens as a client, which checks that the certificate of the server
 * matches the host Camel connects to, unless hostname verification is disabled.
 */
final class MinaClientSslFilter extends SslFilter {

    private final boolean hostnameVerification;

    MinaClientSslFilter(SSLContext sslContext, boolean hostnameVerification) {
        super(sslContext);
        this.hostnameVerification = hostnameVerification;
        if (hostnameVerification) {
            setEndpointIdentificationAlgorithm("HTTPS");
        }
    }

    @Override
    protected SSLEngine createEngine(IoSession session, InetSocketAddress addr) {
        InetSocketAddress peer = addr;
        if (hostnameVerification && addr != null) {
            // check the host as it was given: MINA takes InetSocketAddress.getHostName(), which looks up a host name
            // for an IP address
            peer = InetSocketAddress.createUnresolved(addr.getHostString(), addr.getPort());
        }
        return super.createEngine(session, peer);
    }
}
