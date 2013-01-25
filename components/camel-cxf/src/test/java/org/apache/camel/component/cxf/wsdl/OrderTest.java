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
package org.apache.camel.component.cxf.wsdl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class OrderTest extends CamelSpringTestSupport {
    @BeforeClass
    public static void loadTestSupport() {
        // Need to load the static class first
        CXFTestSupport.getPort1();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/wsdl/camel-route.xml");
    }

    @Test
    public void testCamelWsdl() throws Exception {
        Object body = template.sendBody("http://localhost:" + CXFTestSupport.getPort1() + "/camel-order/?wsdl", ExchangePattern.InOut, null);
        checkWsdl(InputStream.class.cast(body));
    }

    @Test
    public void testCxfWsdl() throws Exception {
        Object body = template.sendBody("http://localhost:" + CXFTestSupport.getPort1() + "/cxf-order/?wsdl", ExchangePattern.InOut, null);
        checkWsdl(InputStream.class.cast(body));
    }

    public void checkWsdl(InputStream in) throws Exception {

        boolean containsOrderComplexType = false;
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("complexType name=\"order\"")) {
                containsOrderComplexType = true;
                // break;
            }

        }

        if (!containsOrderComplexType) {
            throw new RuntimeException("WSDL does not contain complex type defintion for class Order");
        }

    }
}
