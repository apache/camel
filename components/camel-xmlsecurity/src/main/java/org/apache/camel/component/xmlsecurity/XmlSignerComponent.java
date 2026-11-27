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
import org.apache.camel.component.xmlsecurity.processor.XmlSignerConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("xmlsecurity-sign")
public class XmlSignerComponent extends DefaultComponent {

    @Metadata(label = "advanced",
              description = "A shared XmlSignerConfiguration that the endpoints of the component start from: the component copies"
                            + " it for each new endpoint and then sets the options of the endpoint URI on the copy.")
    private XmlSignerConfiguration signerConfiguration;

    public XmlSignerComponent() {
    }

    public XmlSignerComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String remaining,
            Map<String, Object> parameters)
            throws Exception {

        XmlSignerConfiguration config = signerConfiguration != null ? signerConfiguration.copy() : new XmlSignerConfiguration();
        XmlSignerEndpoint endpoint = new XmlSignerEndpoint(uri, this, config);
        endpoint.setName(remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public XmlSignerConfiguration getSignerConfiguration() {
        if (signerConfiguration == null) {
            signerConfiguration = new XmlSignerConfiguration();
        }
        return signerConfiguration;
    }

    public void setSignerConfiguration(XmlSignerConfiguration signerConfiguration) {
        this.signerConfiguration = signerConfiguration;
    }

}
