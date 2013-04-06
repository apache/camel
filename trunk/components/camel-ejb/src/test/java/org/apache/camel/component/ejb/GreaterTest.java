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
package org.apache.camel.component.ejb;

import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;

import junit.framework.TestCase;

/**
 * @version 
 */
public class GreaterTest extends TestCase {

    private InitialContext initialContext;

    protected void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");

        initialContext = new InitialContext(properties);
    }

    public void testGreaterViaLocalInterface() throws Exception {
        Object object = initialContext.lookup("GreaterImplLocal");

        assertNotNull(object);
        assertTrue(object instanceof GreaterLocal);
        GreaterLocal greater = (GreaterLocal) object;

        assertEquals("Hello World", greater.hello("World"));
    }

}
