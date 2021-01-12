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
package org.apache.camel.component.validator.msv;

import javax.xml.XMLConstants;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.component.validator.ValidatorEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.support.processor.validation.ValidatingProcessor;
import org.iso_relax.verifier.jaxp.validation.RELAXNGSchemaFactoryImpl;

/**
 * Validate XML payloads using Multi-Schema Validator (MSV).
 */
@UriEndpoint(firstVersion = "1.1.0", scheme = "msv", title = "MSV", syntax = "msv:resourceUri", producerOnly = true,
             category = { Category.VALIDATION })
public class MsvEndpoint extends ValidatorEndpoint {

    public MsvEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    protected void configureValidator(ValidatingProcessor validator) throws Exception {
        super.configureValidator(validator);
        validator.setSchemaLanguage(XMLConstants.RELAXNG_NS_URI);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // use relax schema factory by default
        if (getSchemaFactory() == null) {
            setSchemaFactory(new RELAXNGSchemaFactoryImpl());
        }
    }

}
