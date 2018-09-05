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
package org.apache.camel.component.validator.msv;

import javax.xml.XMLConstants;

import org.apache.camel.Component;
import org.apache.camel.component.validator.ValidatorEndpoint;
import org.apache.camel.processor.validation.ValidatingProcessor;
import org.apache.camel.spi.UriEndpoint;
import org.iso_relax.verifier.jaxp.validation.RELAXNGSchemaFactoryImpl;

/**
 * Validates the payload of a message using the MSV Library.
 */
@UriEndpoint(firstVersion = "1.1.0", scheme = "msv", title = "MSV", syntax = "msv:resourceUri", producerOnly = true, label = "validation")
public class MsvEndpoint extends ValidatorEndpoint {

    public MsvEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    protected void configureValidator(ValidatingProcessor validator) throws Exception {
        super.configureValidator(validator);
        validator.setSchemaLanguage(XMLConstants.RELAXNG_NS_URI);
        // must use Dom for Msv to work
        validator.setUseDom(true);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // use relax schema factory by default
        if (getSchemaFactory() == null) {
            setSchemaFactory(new RELAXNGSchemaFactoryImpl());
        }
    }

}
