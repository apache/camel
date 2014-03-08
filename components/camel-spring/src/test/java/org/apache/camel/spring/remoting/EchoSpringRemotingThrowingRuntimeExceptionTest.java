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
package org.apache.camel.spring.remoting;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class EchoSpringRemotingThrowingRuntimeExceptionTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/remoting/echo.xml");
    }

    public void testEchoOk() throws Exception {
        String out = template.requestBody("direct:echo", "Claus", String.class);
        assertEquals("Claus Claus", out);
    }
    
    public void testEchoKabom() throws Exception {
        try {
            template.requestBody("direct:echo", "Kabom", String.class);
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(MyEchoRuntimeException.class, e.getCause());
            assertEquals("Damn something went wrong", e.getCause().getMessage());
        }
    }

    public void testRouteOk() throws Exception {
        String out = template.requestBody("direct:start", "Claus", String.class);
        assertEquals("Claus Claus", out);
    }

    public void testRouteKabom() throws Exception {
        try {
            template.requestBody("direct:start", "Kabom", String.class);
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(MyEchoRuntimeException.class, e.getCause());
            assertEquals("Damn something went wrong", e.getCause().getMessage());
        }
    }

}
