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

import java.io.FileNotFoundException;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SaxonInvalidXsltFileTest extends CamelTestSupport {

    @Test
    public void testInvalidStylesheet() throws Exception {
        try {
            template.requestBody("seda:a", "foo");
            fail("Should have thrown an exception due XSL compilation error");
        } catch (CamelExecutionException e) {
            // expected
            Class<?> clazz = e.getCause().getClass();
            assertTrue("Not an expected exception type", clazz == TransformerConfigurationException.class || clazz == FileNotFoundException.class);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:a").to("xslt:org/apache/camel/component/xslt/invalid.xsl?transformerFactoryClass=net.sf.saxon.TransformerFactoryImpl");
            }
        };
    }
}
