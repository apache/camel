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
package org.apache.camel.component.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

import org.apache.camel.impl.engine.DefaultBeanIntrospection;
import org.apache.camel.spi.BeanIntrospection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsServiceLocationHelperTest {

    public static class FakeConnectionFactory implements ConnectionFactory {

        private int invalidCalls;

        public int getInvalidCalls() {
            return invalidCalls;
        }

        @Override
        public Connection createConnection() throws JMSException {
            invalidCalls++;
            return null;
        }

        @Override
        public Connection createConnection(String userName, String password) throws JMSException {
            invalidCalls++;
            return null;
        }

        @Override
        public JMSContext createContext() {
            invalidCalls++;
            return null;
        }

        @Override
        public JMSContext createContext(String userName, String password) {
            invalidCalls++;
            return null;
        }

        @Override
        public JMSContext createContext(String userName, String password, int sessionMode) {
            invalidCalls++;
            return null;
        }

        @Override
        public JMSContext createContext(int sessionMode) {
            invalidCalls++;
            return null;
        }
    }

    public static class TestConnectionFactory extends FakeConnectionFactory {

        private String brokerURL;
        private String user;
        private String username;
        private String userName;

        public TestConnectionFactory(String brokerURL, String user, String username, String userName) {
            super();
            this.brokerURL = brokerURL;
            this.user = user;
            this.username = username;
            this.userName = userName;
        }

        public String getBrokerURL() {
            return brokerURL;
        }

        public void setBrokerURL(String brokerURL) {
            this.brokerURL = brokerURL;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }

    @Test
    public void testGetBrokerURLFromConnectionFactory() {
        BeanIntrospection bi = new DefaultBeanIntrospection();

        String expectedUrl = "tcp://localhost:61616";

        TestConnectionFactory testCf = new TestConnectionFactory(expectedUrl, null, null, null);
        String url = JmsServiceLocationHelper.getBrokerURLFromConnectionFactory(bi, testCf);
        assertEquals(expectedUrl, url);
        assertEquals(0, testCf.getInvalidCalls());
    }

    @Test
    public void testGetUsernameFromConnectionFactory() {
        BeanIntrospection bi = new DefaultBeanIntrospection();

        String expectedUsername = "johndoe";

        // Test with "user" property
        TestConnectionFactory testCf = new TestConnectionFactory(null, expectedUsername, null, null);
        String username = JmsServiceLocationHelper.getUsernameFromConnectionFactory(bi, testCf);
        assertEquals(expectedUsername, username);
        assertEquals(0, testCf.getInvalidCalls());

        // Test with "username" property
        testCf = new TestConnectionFactory(null, null, expectedUsername, null);
        username = JmsServiceLocationHelper.getUsernameFromConnectionFactory(bi, testCf);
        assertEquals(expectedUsername, username);
        assertEquals(0, testCf.getInvalidCalls());

        // Test with "userName" property
        testCf = new TestConnectionFactory(null, null, null, expectedUsername);
        username = JmsServiceLocationHelper.getUsernameFromConnectionFactory(bi, testCf);
        assertEquals(expectedUsername, username);
        assertEquals(0, testCf.getInvalidCalls());
    }

}
