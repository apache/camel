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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.schematron.contant.Constants;
import org.apache.commons.lang.StringUtils;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility Class.
 * <p/>
 * Created by akhettar on 29/12/2013.
 */
public final class Utils {

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

    private Utils() {
        throw new IllegalStateException("This is a utility class");
    }

    /**
     * Evaluate an XPATH expression.
     *
     * @param xpath
     * @param xml
     * @return
     */
    public static String evaluate(final String xpath, final String xml) {
        Map m = new HashMap();
        m.put("svrl", Constants.HTTP_PURL_OCLC_ORG_DSDL_SVRL);

        org.custommonkey.xmlunit.NamespaceContext ctx = new SimpleNamespaceContext(m);
        XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        xpathEngine.setNamespaceContext(ctx);

        try {
            return xpathEngine.evaluate(xpath, XMLUnit.buildControlDocument(xml));
        } catch (Exception e) {
            LOG.error("Failed to apply xpath {} on xml {}", xpath, xml);
            throw new RuntimeCamelException(e);
        }
    }


    /**
     * Get validation status SUCCESS OR FAILURE if there are any schematron validation errors.
     *
     * @param report
     * @return
     */
    public static String getValidationStatus(final String report) {
        return StringUtils.contains(report,
                Constants.FAILED_ASSERT) ? Constants.FAILED : Constants.SUCCESS;
    }
}
