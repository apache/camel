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
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppConnectionFactory</code>
 * 
 * @version 
 */
public class SmppConnectionFactoryTest {

    @Test
    @Ignore("Must be manually tested")
    public void createConnection() throws IOException {
        SmppConfiguration configuration = new SmppConfiguration();
        SmppConnectionFactory factory = SmppConnectionFactory.getInstance(configuration);
        Connection connection = factory.createConnection("localhost", 2775);

        try {
            Assert.assertNotNull(connection);
            Assert.assertTrue(connection.isOpen());            
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    @Test
    @Ignore("Must be manually tested")
    public void createConnectionWithProxyHost() throws IOException {
        SmppConfiguration configuration = new SmppConfiguration();
        configuration.setHttpProxyHost("localhost");
        configuration.setHttpProxyPort(new Integer(3128));
        SmppConnectionFactory factory = SmppConnectionFactory.getInstance(configuration);
        Connection connection = factory.createConnection("localhost", 2775);

        try {
            Assert.assertNotNull(connection);
            Assert.assertTrue(connection.isOpen());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    @Test
    @Ignore("Must be manually tested")
    public void createConnectionWithProxyUsername() throws IOException {
        SmppConfiguration configuration = new SmppConfiguration();
        configuration.setHttpProxyHost("localhost");
        configuration.setHttpProxyPort(new Integer(3128));
        configuration.setHttpProxyUsername("user");
        configuration.setHttpProxyPassword("secret");
        SmppConnectionFactory factory = SmppConnectionFactory.getInstance(configuration);
        Connection connection = factory.createConnection("localhost", 2775);

        try {
            Assert.assertNotNull(connection);
            Assert.assertTrue(connection.isOpen());            
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }     
}