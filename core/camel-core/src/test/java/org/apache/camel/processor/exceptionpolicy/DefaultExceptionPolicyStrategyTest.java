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
package org.apache.camel.processor.exceptionpolicy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.util.HashMap;

import org.apache.camel.AlreadyStoppedException;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ValidationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.processor.errorhandler.DefaultExceptionPolicyStrategy;
import org.apache.camel.processor.errorhandler.ExceptionPolicy;
import org.apache.camel.processor.errorhandler.ExceptionPolicyKey;
import org.apache.camel.reifier.errorhandler.DefaultErrorHandlerReifier;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for DefaultExceptionPolicy
 */
public class DefaultExceptionPolicyStrategyTest extends Assert {

    private DefaultExceptionPolicyStrategy strategy;
    private HashMap<ExceptionPolicyKey, ExceptionPolicy> policies;
    private ExceptionPolicy type1;
    private ExceptionPolicy type2;
    private ExceptionPolicy type3;

    private ExceptionPolicy exceptionPolicy(Class<? extends Throwable> exceptionClass) {
        CamelContext cc = new DefaultCamelContext();
        Route context = new DefaultRoute(cc, null, null, null, null);
        return new DefaultErrorHandlerReifier<>(context, null)
                .createExceptionPolicy(new OnExceptionDefinition(exceptionClass));
    }

    private void setupPolicies() {
        strategy = new DefaultExceptionPolicyStrategy();
        policies = new HashMap<>();
        type1 = exceptionPolicy(CamelExchangeException.class);
        type2 = exceptionPolicy(Exception.class);
        type3 = exceptionPolicy(IOException.class);
        policies.put(new ExceptionPolicyKey(null, CamelExchangeException.class, null), type1);
        policies.put(new ExceptionPolicyKey(null, Exception.class, null), type2);
        policies.put(new ExceptionPolicyKey(null, IOException.class, null), type3);
    }

    private void setupPoliciesNoTopLevelException() {
        // without the top level exception that can be used as fallback
        strategy = new DefaultExceptionPolicyStrategy();
        policies = new HashMap<>();
        type1 = exceptionPolicy(CamelExchangeException.class);
        type3 = exceptionPolicy(IOException.class);
        policies.put(new ExceptionPolicyKey(null, CamelExchangeException.class, null), type1);
        policies.put(new ExceptionPolicyKey(null, IOException.class, null), type3);
    }

    private void setupPoliciesCausedBy() {
        strategy = new DefaultExceptionPolicyStrategy();
        policies = new HashMap<>();
        type1 = exceptionPolicy(FileNotFoundException.class);
        type2 = exceptionPolicy(ConnectException.class);
        type3 = exceptionPolicy(IOException.class);
        policies.put(new ExceptionPolicyKey(null, FileNotFoundException.class, null), type1);
        policies.put(new ExceptionPolicyKey(null, IOException.class, null), type2);
        policies.put(new ExceptionPolicyKey(null, ConnectException.class, null), type3);
    }

    private ExceptionPolicy findPolicy(Exception exception) {
        ExceptionPolicyKey key = strategy.getExceptionPolicy(policies.keySet(), null, exception);
        return policies.get(key);
    }

    @Test
    public void testDirectMatch1() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new CamelExchangeException("", null));
        assertEquals(type1, result);
    }

    @Test
    public void testDirectMatch2() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new Exception(""));
        assertEquals(type2, result);
    }

    @Test
    public void testDirectMatch3() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new IOException(""));
        assertEquals(type3, result);
    }

    @Test
    public void testClosetMatch3() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new ConnectException(""));
        assertEquals(type3, result);

        result = findPolicy(new SocketException(""));
        assertEquals(type3, result);

        result = findPolicy(new FileNotFoundException());
        assertEquals(type3, result);
    }

    @Test
    public void testClosetMatch2() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new ClassCastException(""));
        assertEquals(type2, result);

        result = findPolicy(new NumberFormatException(""));
        assertEquals(type2, result);

        result = findPolicy(new NullPointerException());
        assertEquals(type2, result);
    }

    @Test
    public void testClosetMatch1() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new ValidationException(null, ""));
        assertEquals(type1, result);

        result = findPolicy(new ExchangeTimedOutException(null, 0));
        assertEquals(type1, result);
    }

    @Test
    public void testNoMatch1ThenMatchingJustException() {
        setupPolicies();
        ExceptionPolicy result = findPolicy(new AlreadyStoppedException());
        assertEquals(type2, result);
    }

    @Test
    public void testNoMatch1ThenNull() {
        setupPoliciesNoTopLevelException();
        ExceptionPolicy result = findPolicy(new AlreadyStoppedException());
        assertNull("Should not find an exception policy to use", result);
    }

    @Test
    public void testCausedBy() {
        setupPoliciesCausedBy();

        IOException ioe = new IOException("Damm");
        ioe.initCause(new FileNotFoundException("Somefile not found"));
        ExceptionPolicy result = findPolicy(ioe);
        assertEquals(type1, result);
    }

    @Test
    public void testCausedByWrapped() {
        setupPoliciesCausedBy();

        IOException ioe = new IOException("Damm");
        ioe.initCause(new FileNotFoundException("Somefile not found"));
        ExceptionPolicy result = findPolicy(new RuntimeCamelException(ioe));
        assertEquals(type1, result);
    }

    @Test
    public void testCausedByNotConnected() {
        setupPoliciesCausedBy();

        IOException ioe = new IOException("Damm");
        ioe.initCause(new ConnectException("Not connected"));
        ExceptionPolicy result = findPolicy(ioe);
        assertEquals(type3, result);
    }

    @Test
    public void testCausedByOtherIO() {
        setupPoliciesCausedBy();

        IOException ioe = new IOException("Damm");
        ioe.initCause(new MalformedURLException("Bad url"));
        ExceptionPolicy result = findPolicy(ioe);
        assertEquals(type2, result);
    }

}
