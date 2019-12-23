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
package org.apache.camel.component.cxf.jaxrs;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.component.cxf.common.AbstractHostnameVerifierEndpointConfigurer;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;

public final class HostnameVerifierCxfRsConfigurer extends AbstractHostnameVerifierEndpointConfigurer implements CxfRsConfigurer {

    private HostnameVerifierCxfRsConfigurer(HostnameVerifier hostnameVerifier) {
        super(hostnameVerifier);
    }

    public static CxfRsConfigurer create(HostnameVerifier hostnameVerifier) {
        if (hostnameVerifier == null) {
            return new ChainedCxfRsConfigurer.NullCxfRsConfigurer();
        } else {
            return new HostnameVerifierCxfRsConfigurer(hostnameVerifier);
        }
    }
    @Override
    public void configure(AbstractJAXRSFactoryBean factoryBean) {
    }

    @Override
    public void configureClient(Client client) {
        HTTPConduit httpConduit = (HTTPConduit) WebClient.getConfig(client).getConduit();
        setupHttpConduit(httpConduit);
    }

    @Override
    public void configureServer(Server server) {
    }
}
