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
package org.apache.camel.component.bean;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.jndi.JndiContext;

/**
 * @version 
 */
public class BeanLookupUsingJndiRegistryIssueTest extends TestCase {

    public void testCamelWithJndi() throws Exception {
        JndiContext jndi = new JndiContext();
        jndi.bind("foo", new MyOtherDummyBean());

        CamelContext camel = new DefaultCamelContext(jndi);
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean("foo");
            }
        });
        camel.start();

        String reply = camel.createProducerTemplate().requestBody("direct:start", "Camel", String.class);
        assertEquals("Hello Camel", reply);

        camel.stop();
    }

    @SuppressWarnings("unused")
    private static class MyOtherDummyBean {

        public String hello(String s) {
            return "Hello " + s;
        }
    }
}
