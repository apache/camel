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
package org.apache.camel.component.smpp;

import java.io.IOException;

import org.jsmpp.session.connection.Connection;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppSSLConnectionFactory</code>
 * 
 * @version 
 * @author cmueller
 */
public class SmppSSLConnectionFactoryTest {

    private SmppSSLConnectionFactory factory;

    @Before
    public void setUp() {
        factory = SmppSSLConnectionFactory.getInstance();
    }

    @Test
    public void getInstanceShouldReturnTheSameInstance() {
        assertSame(factory, SmppSSLConnectionFactory.getInstance());
    }

    @Test
    @Ignore("Must be manually tested")
    public void createConnection() throws IOException {
        Connection connection = factory.createConnection("localhost", 2775);
        
        try {
            assertNotNull(connection);
            assertTrue(connection.isOpen());            
        } finally {
            connection.close();
        }
    }
}