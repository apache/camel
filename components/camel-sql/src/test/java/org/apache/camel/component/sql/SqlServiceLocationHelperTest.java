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
package org.apache.camel.component.sql;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.camel.impl.engine.DefaultBeanIntrospection;
import org.apache.camel.spi.BeanIntrospection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlServiceLocationHelperTest {

    public static class FakeDataSource implements DataSource {

        private int invalidCalls;

        public int getInvalidCalls() {
            return invalidCalls;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            invalidCalls++;
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            invalidCalls++;
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            invalidCalls++;
            return null;
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            invalidCalls++;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            invalidCalls++;
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            invalidCalls++;
            return 0;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            invalidCalls++;
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            invalidCalls++;
            return null;
        }

        @Override
        public Connection getConnection() throws SQLException {
            invalidCalls++;
            return null;
        }
    }

    public static class TestDataSource extends FakeDataSource {

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
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

        private String url;
        private String jdbcUrl;
        private String user;
        private String username;

        public TestDataSource(String url, String jdbcUrl, String user, String username) {
            super();
            this.url = url;
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.username = username;
        }

    }

    @Test
    public void testGetJDBCURLFromDataSource() {
        BeanIntrospection bi = new DefaultBeanIntrospection();

        String expectedUrl = "jdbc://expected-url";

        TestDataSource testDs = new TestDataSource(expectedUrl, null, null, null);
        String url = SqlServiceLocationHelper.getJDBCURLFromDataSource(bi, testDs);
        assertEquals(expectedUrl, url);
        assertEquals(0, testDs.getInvalidCalls());

        testDs = new TestDataSource(null, expectedUrl, null, null);
        url = SqlServiceLocationHelper.getJDBCURLFromDataSource(bi, testDs);
        assertEquals(expectedUrl, url);
        assertEquals(0, testDs.getInvalidCalls());

    }

    @Test
    public void testGetUsernameFromConnectionFactory() {
        BeanIntrospection bi = new DefaultBeanIntrospection();

        String expectedUsername = "johndoe";

        TestDataSource testDs = new TestDataSource(null, null, expectedUsername, null);
        String username = SqlServiceLocationHelper.getUsernameFromConnectionFactory(bi, testDs);
        assertEquals(expectedUsername, username);
        assertEquals(0, testDs.getInvalidCalls());

        testDs = new TestDataSource(null, null, null, expectedUsername);
        username = SqlServiceLocationHelper.getUsernameFromConnectionFactory(bi, testDs);
        assertEquals(expectedUsername, username);
        assertEquals(0, testDs.getInvalidCalls());
    }

}
