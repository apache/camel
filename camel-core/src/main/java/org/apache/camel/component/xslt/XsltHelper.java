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
package org.apache.camel.component.xslt;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class XsltHelper {
    private static final Logger LOG = LoggerFactory.getLogger(XsltHelper.class);

    private static final String SAXON_CONFIGURATION_CLASS_NAME = "net.sf.saxon.Configuration";
    private static final String SAXON_EXTENDED_FUNCTION_DEFINITION_CLASS_NAME = "net.sf.saxon.lib.ExtensionFunctionDefinition";

    private XsltHelper() {
    }

    public static void registerSaxonConfiguration(
            CamelContext camelContext, Class<?> factoryClass, TransformerFactory factory, Object saxonConfiguration) throws Exception {

        if (saxonConfiguration != null) {
            Class<?> configurationClass = camelContext.getClassResolver().resolveClass(SAXON_CONFIGURATION_CLASS_NAME);
            if (configurationClass != null) {
                Method method = factoryClass.getMethod("setConfiguration", configurationClass);
                if (method != null) {
                    method.invoke(factory, configurationClass.cast(saxonConfiguration));
                }
            }
        }
    }

    public static void registerSaxonConfigurationProperties(
            CamelContext camelContext, Class<?> factoryClass, TransformerFactory factory, Map<String, Object> saxonConfigurationProperties) throws Exception {

        if (saxonConfigurationProperties != null && !saxonConfigurationProperties.isEmpty()) {
            Method method = factoryClass.getMethod("getConfiguration");
            if (method != null) {
                Object configuration = method.invoke(factory, null);
                if (configuration != null) {
                    method = configuration.getClass().getMethod("setConfigurationProperty", String.class, Object.class);
                    for (Map.Entry<String, Object> entry : saxonConfigurationProperties.entrySet()) {
                        method.invoke(configuration, entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    public static void registerSaxonExtensionFunctions(
            CamelContext camelContext, Class<?> factoryClass, TransformerFactory factory, List<Object> saxonExtensionFunctions) throws Exception {

        if (saxonExtensionFunctions != null && !saxonExtensionFunctions.isEmpty()) {
            Method method = factoryClass.getMethod("getConfiguration");
            if (method != null) {
                Object configuration = method.invoke(factory, null);
                if (configuration != null) {
                    Class<?> extensionClass = camelContext.getClassResolver().resolveMandatoryClass(
                        SAXON_EXTENDED_FUNCTION_DEFINITION_CLASS_NAME, XsltComponent.class.getClassLoader()
                    );

                    method = configuration.getClass().getMethod("registerExtensionFunction", extensionClass);
                    if (method != null) {
                        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                        for (Object extensionFunction : saxonExtensionFunctions) {
                            if (extensionClass.isInstance(extensionFunction)) {
                                LOG.debug("Saxon.registerExtensionFunction {}", extensionFunction);
                                method.invoke(configuration, extensionFunction);
                            }
                        }
                    } else {
                        LOG.warn("Unable to get reference to method registerExtensionFunction on {}", configuration.getClass().getName());
                    }
                } else {
                    LOG.warn("Unable to get Saxon configuration ({}) on {}", SAXON_CONFIGURATION_CLASS_NAME, factory.getClass().getName());
                }
            } else {
                LOG.warn("Unable to get reference to method getConfiguration on {}", factoryClass.getName());
            }
        }
    }
}
