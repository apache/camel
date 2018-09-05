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
package org.apache.camel.component.lumberjack;

import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLContext;

import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * The lumberjack retrieves logs sent over the network using the Lumberjack protocol.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "lumberjack", title = "Lumberjack", syntax = "lumberjack:host:port", consumerOnly = true, consumerClass = LumberjackConsumer.class, label = "log")
public class LumberjackEndpoint extends DefaultEndpoint {
    @UriPath(description = "Network interface on which to listen for Lumberjack")
    @Metadata(required = "true")
    private final String host;
    @UriPath(description = "Network port on which to listen for Lumberjack", defaultValue = "" + LumberjackComponent.DEFAULT_PORT)
    private final int port;
    @UriParam(description = "SSL configuration")
    private SSLContextParameters sslContextParameters;

    LumberjackEndpoint(String endpointUri, LumberjackComponent component, String host, int port) {
        super(endpointUri, component);
        this.host = host;
        this.port = port;
    }

    @Override
    public LumberjackComponent getComponent() {
        return (LumberjackComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("The Lumberjack component cannot be used as a producer");
    }

    @Override
    public LumberjackConsumer createConsumer(Processor processor) throws Exception {
        return new LumberjackConsumer(this, processor, host, port, provideSSLContext());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    private SSLContext provideSSLContext() throws GeneralSecurityException, IOException {
        if (sslContextParameters != null) {
            return sslContextParameters.createSSLContext(getCamelContext());
        } else if (getComponent().getSslContextParameters() != null) {
            return getComponent().getSslContextParameters().createSSLContext(getCamelContext());
        } else {
            return null;
        }
    }
}
