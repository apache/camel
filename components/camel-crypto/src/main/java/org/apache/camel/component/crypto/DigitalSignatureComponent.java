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
package org.apache.camel.component.crypto;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("crypto")
public class DigitalSignatureComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private DigitalSignatureConfiguration configuration = new DigitalSignatureConfiguration();

    public DigitalSignatureComponent() {
    }

    public DigitalSignatureComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext");

        DigitalSignatureConfiguration config = getConfiguration().copy();

        config.setCamelContext(getCamelContext());
        try {
            config.setCryptoOperation(new URI(remaining).getScheme());
        } catch (Exception e) {
            throw new MalformedURLException(String.format("An invalid crypto uri was provided '%s'."
                    + " Check the uri matches the format crypto:sign or crypto:verify", uri));
        }
        Endpoint endpoint = new DigitalSignatureEndpoint(uri, this, config);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public DigitalSignatureConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared DigitalSignatureConfiguration as configuration
     */
    public void setConfiguration(DigitalSignatureConfiguration configuration) {
        this.configuration = configuration;
    }
}
