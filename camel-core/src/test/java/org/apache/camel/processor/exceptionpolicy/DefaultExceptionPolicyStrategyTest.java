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
package org.apache.camel.processor.exceptionpolicy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.HashMap;

import junit.framework.TestCase;

import org.apache.camel.AlreadyStoppedException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ValidationException;
import org.apache.camel.model.ExceptionType;

/**
 * Unit test for DefaultExceptionPolicy 
 */
public class DefaultExceptionPolicyStrategyTest extends TestCase {

    private DefaultExceptionPolicyStrategy strategy;
    private HashMap<ExceptionPolicyKey, ExceptionType> policies;
    private ExceptionType type1;
    private ExceptionType type2;
    private ExceptionType type3;

    private void setupPolicies() {
        strategy = new DefaultExceptionPolicyStrategy();
        policies = new HashMap<ExceptionPolicyKey, ExceptionType>();
        type1 = new ExceptionType(CamelExchangeException.class);
        type2 = new ExceptionType(Exception.class);
        type3 = new ExceptionType(IOException.class);
        policies.put(ExceptionPolicyKey.newInstance(CamelExchangeException.class), type1);
        policies.put(ExceptionPolicyKey.newInstance(Exception.class), type2);
        policies.put(ExceptionPolicyKey.newInstance(IOException.class), type3);
    }

    private void setupPoliciesNoTopLevelException() {
        // without the top level exception that can be used as fallback
        strategy = new DefaultExceptionPolicyStrategy();
        policies = new HashMap<ExceptionPolicyKey, ExceptionType>();
        type1 = new ExceptionType(CamelExchangeException.class);
        type3 = new ExceptionType(IOException.class);
        policies.put(ExceptionPolicyKey.newInstance(CamelExchangeException.class), type1);
        policies.put(ExceptionPolicyKey.newInstance(IOException.class), type3);
    }

    public void testDirectMatch1() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new CamelExchangeException("", null));
        assertEquals(type1, result);
    }

    public void testDirectMatch2() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new Exception(""));
        assertEquals(type2, result);
    }

    public void testDirectMatch3() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new IOException(""));
        assertEquals(type3, result);
    }

    public void testClosetMatch3() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new ConnectException(""));
        assertEquals(type3, result);

        result = strategy.getExceptionPolicy(policies, null, new SocketException(""));
        assertEquals(type3, result);

        result = strategy.getExceptionPolicy(policies, null, new FileNotFoundException());
        assertEquals(type3, result);
    }

    public void testClosetMatch2() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new ClassCastException(""));
        assertEquals(type2, result);

        result = strategy.getExceptionPolicy(policies, null, new NumberFormatException(""));
        assertEquals(type2, result);

        result = strategy.getExceptionPolicy(policies, null, new NullPointerException());
        assertEquals(type2, result);
    }

    public void testClosetMatch1() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new ValidationException(null, ""));
        assertEquals(type1, result);

        result = strategy.getExceptionPolicy(policies, null, new ExchangeTimedOutException(null, 0));
        assertEquals(type1, result);
    }

    public void testNoMatch1ThenMatchingJustException() {
        setupPolicies();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new AlreadyStoppedException());
        assertEquals(type2, result);
    }

    public void testNoMatch1ThenNull() {
        setupPoliciesNoTopLevelException();
        ExceptionType result = strategy.getExceptionPolicy(policies, null, new AlreadyStoppedException());
        assertNull("Should not find an exception policy to use", result);
    }

}
