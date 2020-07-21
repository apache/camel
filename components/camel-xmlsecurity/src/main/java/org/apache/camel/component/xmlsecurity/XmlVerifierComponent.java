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
package org.apache.camel.component.xmlsecurity;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("xmlsecurity-verify")
public class XmlVerifierComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private XmlVerifierConfiguration verifierConfiguration;

    public XmlVerifierComponent() {
    }

    public XmlVerifierComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining,
                                      Map<String, Object> parameters) throws Exception {
        XmlVerifierConfiguration config = verifierConfiguration != null ? verifierConfiguration.copy() : new XmlVerifierConfiguration();
        XmlVerifierEndpoint endpoint = new XmlVerifierEndpoint(uri, this, config);
        endpoint.setName(remaining);
        setProperties(endpoint, parameters);
        return endpoint;
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
