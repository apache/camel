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
package org.apache.camel.component.schematron.util;

import java.util.Collections;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.schematron.constant.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.builder.Input;
import org.xmlunit.xpath.JAXPXPathEngine;

/**
 * Utility Class.
 */
public final class Utils {

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

    private Utils() {
        throw new IllegalStateException("This is a utility class");
    }

    /**
     * Evaluate an XPATH expression.
     */
    public static String evaluate(final String xpath, final String xml) {
        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(Collections.singletonMap("svrl", Constants.HTTP_PURL_OCLC_ORG_DSDL_SVRL));
        try {
            return xpathEngine.evaluate(xpath, Input.fromString(xml).build());
        } catch (Exception e) {
            LOG.error("Failed to apply xpath {} on xml {}", xpath, xml);
            throw new RuntimeCamelException(e);
        }
    }


}
