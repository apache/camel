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
package org.apache.camel.reifier.rest;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.model.rest.RestBindingDefinition;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.processor.RestBindingAdvice;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;

public class RestBindingReifier extends AbstractReifier {

    private final RestBindingDefinition definition;

    public RestBindingReifier(Route route, RestBindingDefinition definition) {
        super(route);
        this.definition = definition;
    }

    public RestBindingAdvice createRestBindingAdvice() throws Exception {
        RestConfiguration config = CamelContextHelper.getRestConfiguration(camelContext, definition.getComponent());

        // these options can be overridden per rest verb
        String mode = config.getBindingMode().name();
        if (definition.getBindingMode() != null) {
            mode = parse(RestBindingMode.class, definition.getBindingMode()).name();
        }
        boolean cors = config.isEnableCORS();
        if (definition.getEnableCORS() != null) {
            cors = parseBoolean(definition.getEnableCORS(), false);
        }
        boolean skip = config.isSkipBindingOnErrorCode();
        if (definition.getSkipBindingOnErrorCode() != null) {
            skip = parseBoolean(definition.getSkipBindingOnErrorCode(), false);
        }
        boolean validation = config.isClientRequestValidation();
        if (definition.getClientRequestValidation() != null) {
            validation = parseBoolean(definition.getClientRequestValidation(), false);
        }

        // cors headers
        Map<String, String> corsHeaders = config.getCorsHeaders();

        if ("off".equals(mode)) {
            // binding mode is off, so create a off mode binding processor
            return new RestBindingAdvice(camelContext, null, null, null, null,
                                         parseString(definition.getConsumes()), parseString(definition.getProduces()), mode, skip, validation, cors, corsHeaders,
                                         definition.getDefaultValues(), definition.getRequiredBody() != null ? definition.getRequiredBody() : false,
                                         definition.getRequiredQueryParameters(), definition.getRequiredHeaders());
        }

        // setup json data format
        DataFormat json = null;
        DataFormat outJson = null;
        if (mode.contains("json") || "auto".equals(mode)) {
            String name = config.getJsonDataFormat();
            if (name != null) {
                // must only be a name, not refer to an existing instance
                Object instance = lookupByName(name);
                if (instance != null) {
                    throw new IllegalArgumentException("JsonDataFormat name: " + name + " must not be an existing bean instance from the registry");
                }
            } else {
                name = "json-jackson";
            }
            // this will create a new instance as the name was not already
            // pre-created
            json = camelContext.resolveDataFormat(name);
            outJson = camelContext.resolveDataFormat(name);

            if (json != null) {
                setupJson(config, parseString(definition.getType()), parseString(definition.getOutType()), json, outJson);
            }
        }

        // setup xml data format
        DataFormat jaxb = null;
        DataFormat outJaxb = null;
        if (mode.contains("xml") || "auto".equals(mode)) {
            String name = config.getXmlDataFormat();
            if (name != null) {
                // must only be a name, not refer to an existing instance
                Object instance = lookupByName(name);
                if (instance != null) {
                    throw new IllegalArgumentException("XmlDataFormat name: " + name + " must not be an existing bean instance from the registry");
                }
            } else {
                name = "jaxb";
            }
            // this will create a new instance as the name was not already
            // pre-created
            jaxb = camelContext.resolveDataFormat(name);
            outJaxb = camelContext.resolveDataFormat(name);

            // is xml binding required?
            if (mode.contains("xml") && jaxb == null) {
                throw new IllegalArgumentException("XML DataFormat " + name + " not found.");
            }

            if (jaxb != null) {
                // to setup JAXB we need to use camel-xml-jaxb
                camelContext.adapt(ExtendedCamelContext.class).getRestBindingJaxbDataFormatFactory()
                        .setupJaxb(camelContext, config, parseString(definition.getType()), parseString(definition.getOutType()), jaxb, outJaxb);
            }
        }

        return new RestBindingAdvice(camelContext, json, jaxb, outJson, outJaxb,
                                     parseString(definition.getConsumes()), parseString(definition.getProduces()),
                                     mode, skip, validation, cors, corsHeaders,
                                     definition.getDefaultValues(), definition.getRequiredBody() != null ? definition.getRequiredBody() : false,
                                     definition.getRequiredQueryParameters(), definition.getRequiredHeaders());
    }

    protected void setupJson(RestConfiguration config, String type, String outType, DataFormat json, DataFormat outJson) throws Exception {
        Class<?> clazz = null;
        if (type != null) {
            String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
            clazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
        }
        if (clazz != null) {
            camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(camelContext, json, "unmarshalType", clazz);
            camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(camelContext, json, "useList", type.endsWith("[]"));
        }
        setAdditionalConfiguration(config, json, "json.in.");

        Class<?> outClazz = null;
        if (outType != null) {
            String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
            outClazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
        }
        if (outClazz != null) {
            camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(camelContext, outJson, "unmarshalType", outClazz);
            camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(camelContext, outJson, "useList", outType.endsWith("[]"));
        }
        setAdditionalConfiguration(config, outJson, "json.out.");
    }

    private void setAdditionalConfiguration(RestConfiguration config, DataFormat dataFormat, String prefix) throws Exception {
        if (config.getDataFormatProperties() != null && !config.getDataFormatProperties().isEmpty()) {
            // must use a copy as otherwise the options gets removed during
            // introspection setProperties
            Map<String, Object> copy = new HashMap<>();

            // filter keys on prefix
            // - either its a known prefix and must match the prefix parameter
            // - or its a common configuration that we should always use
            for (Map.Entry<String, Object> entry : config.getDataFormatProperties().entrySet()) {
                String key = entry.getKey();
                String copyKey;
                boolean known = isKeyKnownPrefix(key);
                if (known) {
                    // remove the prefix from the key to use
                    copyKey = key.substring(prefix.length());
                } else {
                    // use the key as is
                    copyKey = key;
                }
                if (!known || key.startsWith(prefix)) {
                    copy.put(copyKey, entry.getValue());
                }
            }

            PropertyBindingSupport.build().bind(camelContext, dataFormat, copy);
        }
    }

    private boolean isKeyKnownPrefix(String key) {
        return key.startsWith("json.in.") || key.startsWith("json.out.") || key.startsWith("xml.in.") || key.startsWith("xml.out.");
    }

}
