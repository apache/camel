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
package org.apache.camel.builder.saxon;

import javax.xml.xpath.XPathFactory;

import net.sf.saxon.lib.NamespaceConstant;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.xpath.XPath;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class XPathAnnotationResultTypeTest extends CamelTestSupport {
    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {

        String response = (String) template.requestBody("direct:in1", "<a><b>hello</b></a>");
        assertEquals("HELLO", response);
        
        response = (String) template.requestBody("direct:in2", "<a><b>hello</b></a>");
        assertEquals("HELLO", response);
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("myBean", myBean);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                System.setProperty(XPathFactory.DEFAULT_PROPERTY_NAME + ":" + NamespaceConstant.OBJECT_MODEL_SAXON, "net.sf.saxon.xpath.XPathFactoryImpl");
                from("direct:in1").bean("myBean", "readImplicit");
                from("direct:in2").bean("myBean", "readExplicit");
            }
        };
    }

    public static class MyBean {
        public String abText;

        public String readImplicit(@XPath("upper-case(//a/b/text())") String abText) {
            return abText;
        }

        public String readExplicit(@XPath(value = "upper-case(//a/b/text())", resultType = String.class) String abText) {
            return abText;
        }
    }
}
