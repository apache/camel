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
package org.apache.camel.component.jt400;

import java.util.Locale;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import com.ibm.as400.access.ConnectionPoolException;

/**
 * Mock {@code AS400ConnectionPool} implementation, useful in unit testing JT400 endpoints.
 */
public class MockAS400ConnectionPool extends AS400ConnectionPool {

    private static final long serialVersionUID = -7473444280370756827L;

    public MockAS400ConnectionPool() {
        setRunMaintenance(false);
        setThreadUsed(false);
    }

    @Deprecated
    @Override
    public AS400 getConnection(String systemName, String userID) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public AS400 getConnection(String systemName, String userID, int service) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password) throws ConnectionPoolException {
        return new AS400(systemName, userID, password);
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password, int service) throws ConnectionPoolException {
        return getConnection(systemName, userID, password);
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password, int service, Locale locale) throws ConnectionPoolException {
        return getConnection(systemName, userID, password, locale);
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password, Locale locale) throws ConnectionPoolException {
        AS400 connection = getConnection(systemName, userID, password);
        connection.setLocale(locale);
        return connection;
    }

}
