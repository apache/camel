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

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version $Revision$
 */
public class BeanExplicitMethodAmbiguousTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy", new MyDummyBean());
        return jndi;
    }

    public void testBeanExplicitMethodAmbiguous() throws Exception {
        try {
            template.requestBody("direct:hello", "Camel");
            fail("Should thrown an exception");
        } catch (Exception e) {
            AmbiguousMethodCallException cause = assertIsInstanceOf(AmbiguousMethodCallException.class, e.getCause());
            assertEquals(2, cause.getMethods().size());
        }
    }

    public void testBeanExplicitMethodHandler() throws Exception {
        String out = template.requestBody("direct:bye", "Camel", String.class);
        assertEquals("Bye Camel", out);
    }
    
    public void testBeanImplicitMethodInvocationStringBody() throws Exception {
        String out = template.requestBody("direct:foo", "Camel", String.class);
        assertEquals("String", out);
    }
    
    public void testBeanImplicitMethodInvocationReaderBody() throws Exception {
        String out = template.requestBody("direct:foo", new StringReader("Camel"), String.class);
        assertEquals("Reader", out);
    }
    
    public void testBeanImplicitMethodInvocationInputStreamBody() throws Exception {
        String out = template.requestBody("direct:foo", new ByteArrayInputStream("Camel".getBytes()), String.class);
        assertEquals("InputStream", out);
    }
    
    public void testBeanExplicitMethodInvocationString() throws Exception {
        String out = template.requestBody("direct:explicitString", new ByteArrayInputStream("Camel".getBytes()), String.class);
        assertEquals("String", out);
    }
    
    public void testBeanExplicitMethodInvocationReader() throws Exception {
        String out = template.requestBody("direct:explicitReader", new ByteArrayInputStream("Camel".getBytes()), String.class);
        assertEquals("Reader", out);
    }

    public void testBeanExplicitMethodInvocationInputStream() throws Exception {
        String out = template.requestBody("direct:explicitInputStream", "Camel", String.class);
        assertEquals("InputStream", out);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:hello").beanRef("dummy", "hello");

                from("direct:bye").beanRef("dummy");
                
                from("direct:foo").beanRef("dummy", "bar");
                
                from("direct:explicitString").to("bean:dummy?method=bar&parameterType=java.lang.String");
                
                from("direct:explicitReader").to("bean:dummy?method=bar&parameterType=java.io.Reader");
                
                from("direct:explicitInputStream").to("bean:dummy?method=bar&parameterType=java.io.InputStream");
            }
        };
    }
}