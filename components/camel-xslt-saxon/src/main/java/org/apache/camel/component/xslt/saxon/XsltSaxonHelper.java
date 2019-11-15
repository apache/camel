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
package org.apache.camel.component.xslt.saxon;

import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class XsltSaxonHelper {
    private static final Logger LOG = LoggerFactory.getLogger(XsltSaxonHelper.class);

    private XsltSaxonHelper() {
    }

    public static void registerSaxonConfiguration(TransformerFactoryImpl factory, Configuration saxonConfiguration) throws Exception {
        if (saxonConfiguration != null) {
            factory.setConfiguration(saxonConfiguration);
        }
    }

    public static void registerSaxonConfigurationProperties(TransformerFactoryImpl factory,
                                                            Map<String, Object> saxonConfigurationProperties) throws Exception {
        if (saxonConfigurationProperties != null && !saxonConfigurationProperties.isEmpty()) {
            for (Map.Entry<String, Object> entry : saxonConfigurationProperties.entrySet()) {
                factory.getConfiguration().setConfigurationProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void registerSaxonExtensionFunctions(TransformerFactoryImpl factory, List<Object> saxonExtensionFunctions) throws Exception {
        if (saxonExtensionFunctions != null && !saxonExtensionFunctions.isEmpty()) {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            for (Object extensionFunction : saxonExtensionFunctions) {
                if (extensionFunction instanceof ExtensionFunctionDefinition) {
                    LOG.debug("Saxon.registerExtensionFunction {}", extensionFunction);
                    factory.getConfiguration().registerExtensionFunction((ExtensionFunctionDefinition) extensionFunction);
                }
            }
        }
    }
    
}
