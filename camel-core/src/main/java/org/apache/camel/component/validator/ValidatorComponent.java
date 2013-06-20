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
package org.apache.camel.component.validator;

import java.io.InputStream;
import java.util.Map;

import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.Endpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.processor.validation.ValidatingProcessor;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/validation.html">Validator Component</a>
 * for validating XML against some schema
 */
public class ValidatorComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(ValidatorComponent.class);

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String resourceUri = remaining;
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), resourceUri);
        byte[] bytes = null;
        try {
            bytes = IOConverter.toBytes(is);
        } finally {
            // and make sure to close the input stream after the schema has been loaded
            IOHelper.close(is);
        }

        ValidatingProcessor validator = new ValidatingProcessor();
        validator.setSchemaAsByteArray(bytes);
        LOG.debug("{} using schema resource: {}", this, resourceUri);
        configureValidator(validator, uri, remaining, parameters);

        // force loading of schema at create time otherwise concurrent
        // processing could cause thread safe issues for the javax.xml.validation.SchemaFactory
        validator.loadSchema();

        return new ProcessorEndpoint(uri, this, validator);
    }

    protected void configureValidator(ValidatingProcessor validator, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LSResourceResolver resourceResolver = resolveAndRemoveReferenceParameter(parameters, "resourceResolver", LSResourceResolver.class);
        if (resourceResolver != null) {
            validator.setResourceResolver(resourceResolver);
        } else {
            validator.setResourceResolver(new DefaultLSResourceResolver(getCamelContext(), remaining));
        }

        setProperties(validator, parameters);
    }
}