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
import com.ibm.as400.security.auth.ProfileTokenCredential;

/**
 * Mock {@code AS400ConnectionPool} implementation, useful in unit testing JT400 endpoints with secure option=true.
 */
public class MockAS400SecureConnectionPool extends AS400ConnectionPool {

    public MockAS400SecureConnectionPool() {
        setRunMaintenance(false);
        setThreadUsed(false);
    }

    @Override
    public AS400 getSecureConnection(String systemName, String userID, String password) throws ConnectionPoolException {
        return new AS400(systemName, userID, password);
    }

    @Deprecated
    @Override
    public AS400 getSecureConnection(String systemName, String userID) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AS400 getSecureConnection(String systemName, String userID, String password, int i) throws ConnectionPoolException {
        return new AS400(systemName, userID, password);
    }

    @Deprecated
    @Override
    public AS400 getSecureConnection(String systemName, String userID, int i) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AS400 getSecureConnection(String systemName, String userID, ProfileTokenCredential profileTokenCredential) throws ConnectionPoolException {
        return new AS400(systemName, userID);
    }

    @Override
    public AS400 getSecureConnection(String systemName, String userID, ProfileTokenCredential profileTokenCredential, int i) throws ConnectionPoolException {
        return new AS400(systemName, userID);
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
        throw new UnsupportedOperationException();
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password, int service) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password, int service, Locale locale) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AS400 getConnection(String systemName, String userID, String password, Locale locale) throws ConnectionPoolException {
        throw new UnsupportedOperationException();
    }

}
