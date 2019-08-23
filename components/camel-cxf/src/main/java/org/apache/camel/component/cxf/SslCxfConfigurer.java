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
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.common.AbstractSslEndpointConfigurer;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.apache.cxf.transport.http.HTTPConduit;

public final class SslCxfConfigurer extends AbstractSslEndpointConfigurer implements CxfConfigurer {

    private SslCxfConfigurer(SSLContextParameters sslContextParameters,
                             CamelContext camelContext) {
        super(sslContextParameters, camelContext);
    }

    public static CxfConfigurer create(SSLContextParameters sslContextParameters, CamelContext camelContext) {
        if (sslContextParameters == null) {
            return new ChainedCxfConfigurer.NullCxfConfigurer();
        } else {
            return new SslCxfConfigurer(sslContextParameters, camelContext);
        }
    }

    @Override
    public void configure(AbstractWSDLBasedEndpointFactory factoryBean) {
    }

    @Override
    public void configureClient(Client client) {
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        setupHttpConduit(httpConduit);
    }

    @Override
    public void configureServer(Server server) {
    }
}
