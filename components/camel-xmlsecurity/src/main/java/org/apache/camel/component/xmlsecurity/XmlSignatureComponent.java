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
package org.apache.camel.component.xmlsecurity;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.xmlsecurity.processor.XmlSignerConfiguration;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierConfiguration;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

public class XmlSignatureComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private XmlSignerConfiguration signerConfiguration;
    @Metadata(label = "advanced")
    private XmlVerifierConfiguration verifierConfiguration;

    public XmlSignatureComponent() {
    }

    public XmlSignatureComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining,
                                      Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext");

        String scheme;
        String name;
        try {
            URI u = new URI(remaining);
            scheme = u.getScheme();
            name = u.getPath();
        } catch (Exception e) {
            throw new MalformedURLException(
                String.format(
                    "An invalid xmlsecurity uri was provided '%s'."
                    + " Check the uri matches the format xmlsecurity:sign://<name> or xmlsecurity:verify:<name>",
                    uri
                )
            );
        }
        XmlSignatureEndpoint endpoint;
        if ("sign".equals(scheme)) {
            XmlSignerConfiguration config = getSignerConfiguration().copy();
            endpoint = new XmlSignerEndpoint(uri, this, config);
        } else if ("verify".equals(scheme)) {
            XmlVerifierConfiguration config = getVerifierConfiguration().copy();
            endpoint = new XmlVerifierEndpoint(uri, this, config);
        } else {
            throw new IllegalStateException(
                String.format(
                    "Endpoint uri '%s'" + " is wrong configured. Operation '%s'"
                    + " is not supported. Supported operations are: sign, verify",
                    uri, scheme
                )
            );
        }
        setProperties(endpoint.getConfiguration(), parameters);
        endpoint.getConfiguration().setCamelContext(getCamelContext());
        endpoint.setCommand(XmlCommand.valueOf(scheme));
        endpoint.setName(name);
        return endpoint;
    }

    public XmlSignerConfiguration getSignerConfiguration() {
        if (signerConfiguration == null) {
            signerConfiguration = new XmlSignerConfiguration();
        }
        return signerConfiguration;
    }

    /**
     * To use a shared XmlSignerConfiguration configuration to use as base for configuring endpoints.
     */
    public void setSignerConfiguration(XmlSignerConfiguration signerConfiguration) {
        this.signerConfiguration = signerConfiguration;
    }

    public XmlVerifierConfiguration getVerifierConfiguration() {
        if (verifierConfiguration == null) {
            verifierConfiguration = new XmlVerifierConfiguration();
        }
        return verifierConfiguration;
    }

    /**
     * To use a shared XmlVerifierConfiguration configuration to use as base for configuring endpoints.
     */
    public void setVerifierConfiguration(XmlVerifierConfiguration verifierConfiguration) {
        this.verifierConfiguration = verifierConfiguration;
    }

}
