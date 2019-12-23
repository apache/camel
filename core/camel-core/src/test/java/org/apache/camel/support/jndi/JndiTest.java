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
package org.apache.camel.support.jndi;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.camel.TestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Before;
import org.junit.Test;

public class JndiTest extends TestSupport {
    protected Context context;

    public static Context createInitialContext() throws Exception {
        InputStream in = JndiTest.class.getClassLoader().getResourceAsStream("jndi-example.properties");
        try {
            assertNotNull("Cannot find jndi-example.properties on the classpath!", in);
            Properties properties = new Properties();
            properties.load(in);
            return new InitialContext(new Hashtable<>(properties));
        } finally {
            IOHelper.close(in);
        }
    }

    @Test
    public void testLookupOfSimpleName() throws Exception {
        Object value = assertLookup("foo");
        assertEquals("foo", "bar", value);
    }

    protected Object assertLookup(String name) throws NamingException {
        Object value = context.lookup(name);
        assertNotNull("Should have found JNDI entry: " + name + " in context: " + context, value);
        return value;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        context = createInitialContext();
    }
}
