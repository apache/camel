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
package org.apache.camel.support.processor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;

/**
 * Factory to create {@link RestBindingAdvice} from the given configuration.
 */
public class RestBindingAdviceFactory {

    /**
     * Builds the {@link RestBindingAdvice} from the given configuration
     *
     * @param  camelContext the camel context
     * @param  bc           the binding configuration
     * @return              the created binding advice
     */
    public static RestBindingAdvice build(CamelContext camelContext, RestBindingConfiguration bc) throws Exception {
        String mode = bc.getBindingMode();

        // setup json data format
        RestConfiguration config = camelContext.getRestConfiguration();
        DataFormat json = null;
        DataFormat outJson = null;
        // include json if we have client request validator as we need a json parser
        if (mode.contains("json") || "auto".equals(mode) || bc.isClientRequestValidation()) {
            String name = config.getJsonDataFormat();
            if (name != null) {
                // must only be a name, not refer to an existing instance
                Object instance = lookupByName(camelContext, name);
                if (instance != null) {
                    throw new IllegalArgumentException(
                            "JsonDataFormat name: " + name + " must not be an existing bean instance from the registry");
                }
            } else {
                name = "jackson";
            }
            boolean optional = "off".equals(mode) && bc.isClientRequestValidation();
            // this will create a new instance as the name was not already pre-created
            if (optional) {
                try {
                    json = camelContext.createDataFormat(name);
                } catch (IllegalArgumentException e) {
                    // ignore
                }
            } else {
                json = camelContext.createDataFormat(name);
                outJson = camelContext.createDataFormat(name);
            }

            if (json != null) {
                setupJson(camelContext, config,
                        bc.getType(), bc.getTypeClass(),
                        bc.getOutType(), bc.getOutTypeClass(),
                        json, outJson);
            }
        }

        // setup xml data format
        DataFormat jaxb = null;
        DataFormat outJaxb = null;
        if (mode.contains("xml") || "auto".equals(mode)) {
            String name = config.getXmlDataFormat();
            if (name != null) {
                // must only be a name, not refer to an existing instance
                Object instance = lookupByName(camelContext, name);
                if (instance != null) {
                    throw new IllegalArgumentException(
                            "XmlDataFormat name: " + name + " must not be an existing bean instance from the registry");
                }
            } else {
                name = "jaxb";
            }
            // this will create a new instance as the name was not already
            // pre-created
            jaxb = camelContext.createDataFormat(name);
            outJaxb = camelContext.createDataFormat(name);

            // is xml binding required?
            if (mode.contains("xml") && jaxb == null) {
                throw new IllegalArgumentException("XML DataFormat " + name + " not found.");
            }

            if (jaxb != null) {
                // to setup JAXB we need to use camel-jaxb
                PluginHelper.getRestBindingJaxbDataFormatFactory(camelContext).setupJaxb(camelContext, config,
                        bc.getType(), bc.getTypeClass(),
                        bc.getOutType(), bc.getOutTypeClass(),
                        jaxb, outJaxb);
            }
        }

        return new RestBindingAdvice(
                camelContext, json, jaxb, outJson, outJaxb,
                bc.getConsumes(), bc.getProduces(), mode, bc.isSkipBindingOnErrorCode(), bc.isClientRequestValidation(),
                bc.isEnableCORS(),
                bc.isEnableNoContentResponse(), bc.getCorsHeaders(),
                bc.getQueryDefaultValues(), bc.getQueryAllowedValues(), bc.isRequiredBody(), bc.getRequiredQueryParameters(),
                bc.getRequiredHeaders());
    }

    protected static void setupJson(
            CamelContext camelContext,
            RestConfiguration config, String type, Class<?> typeClass, String outType, Class<?> outTypeClass, DataFormat json,
            DataFormat outJson)
            throws Exception {
        Class<?> clazz = null;
        boolean useList = false;

        if (typeClass != null) {
            useList = typeClass.isArray();
            clazz = useList ? typeClass.getComponentType() : typeClass;
        } else if (type != null) {
            useList = type.endsWith("[]");
            String typeName = useList ? type.substring(0, type.length() - 2) : type;
            clazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
        }
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(camelContext);
        if (clazz != null) {
            beanIntrospection.setProperty(camelContext, json,
                    "unmarshalType", clazz);
            beanIntrospection.setProperty(camelContext, json, "useList",
                    useList);
        }

        setAdditionalConfiguration(camelContext, config, json, "json.in.");

        Class<?> outClazz = null;
        boolean outUseList = false;

        if (outTypeClass != null) {
            outUseList = outTypeClass.isArray();
            outClazz = outUseList ? outTypeClass.getComponentType() : outTypeClass;
        } else if (outType != null) {
            outUseList = outType.endsWith("[]");
            String typeName = outUseList ? outType.substring(0, outType.length() - 2) : outType;
            outClazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
        }

        if (outClazz != null) {
            beanIntrospection.setProperty(camelContext, outJson,
                    "unmarshalType", outClazz);
            beanIntrospection.setProperty(camelContext, outJson, "useList",
                    outUseList);
        }

        setAdditionalConfiguration(camelContext, config, outJson, "json.out.");
    }

    private static void setAdditionalConfiguration(
            CamelContext camelContext, RestConfiguration config, DataFormat dataFormat, String prefix) {
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

    private static boolean isKeyKnownPrefix(String key) {
        return key.startsWith("json.in.") || key.startsWith("json.out.") || key.startsWith("xml.in.")
                || key.startsWith("xml.out.");
    }

    private static Object lookupByName(CamelContext camelContext, String name) {
        if (name == null) {
            return null;
        }

        if (EndpointHelper.isReferenceParameter(name)) {
            return EndpointHelper.resolveReferenceParameter(camelContext, name, Object.class, false);
        } else {
            return camelContext.getRegistry().lookupByName(name);
        }
    }

}
