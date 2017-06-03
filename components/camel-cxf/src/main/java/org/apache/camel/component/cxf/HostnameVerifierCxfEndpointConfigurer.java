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
package org.apache.camel.component.cxf;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.component.cxf.common.AbstractHostnameVerifierEndpointConfigurer;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.apache.cxf.transport.http.HTTPConduit;

public final class HostnameVerifierCxfEndpointConfigurer extends AbstractHostnameVerifierEndpointConfigurer implements CxfEndpointConfigurer {

    private HostnameVerifierCxfEndpointConfigurer(HostnameVerifier hostnameVerifier) {
        super(hostnameVerifier);
    }

    public static CxfEndpointConfigurer create(HostnameVerifier hostnameVerifier) {
        if (hostnameVerifier == null) {
            return new ChainedCxfEndpointConfigurer.NullCxfEndpointConfigurer();
        } else {
            return new HostnameVerifierCxfEndpointConfigurer(hostnameVerifier);
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
