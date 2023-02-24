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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierConfiguration;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants.SCHEME_VERIFIER;

/**
 * Verify XML payloads using the XML signature specification.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = SCHEME_VERIFIER, title = "XML Security Verify",
             syntax = "xmlsecurity-verify:name", producerOnly = true, category = { Category.SECURITY, Category.TRANSFORMATION },
             headersClass = XmlSignatureConstants.class)
public class XmlVerifierEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String name;
    @UriParam
    private XmlVerifierConfiguration configuration;

    public XmlVerifierEndpoint(String uri, XmlVerifierComponent component,
                               XmlVerifierConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public String getName() {
        return name;
    }

    /**
     * The name part in the URI can be chosen by the user to distinguish between different verify endpoints within the
     * camel context.
     */
    public void setName(String name) {
        this.name = name;
    }

    public XmlVerifierConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Configuration
     */
    public void setConfiguration(XmlVerifierConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        Processor processor = new XmlVerifierProcessor(getCamelContext(), getConfiguration());
        return new XmlSecurityProducer(this, processor);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Object ns = configuration.getOutputNodeSearch();
        if (ns instanceof String && ns.toString().startsWith("#")) {
            // its a reference lookup

        }

    }
}
