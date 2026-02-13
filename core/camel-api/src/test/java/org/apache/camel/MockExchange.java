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
package org.apache.camel;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.clock.Clock;
import org.apache.camel.spi.UnitOfWork;

public class MockExchange implements Exchange {

    private Map<String, Object> properties;
    private Map<String, Object> variables;

    public MockExchange() {
        this.properties = new HashMap<>();
        this.variables = new HashMap<>();
    }

    @Override
    public ExchangePattern getPattern() {
        throw new UnsupportedOperationException("Unimplemented method 'getPattern'");
    }

    @Override
    public void setPattern(ExchangePattern pattern) {
        throw new UnsupportedOperationException("Unimplemented method 'setPattern'");
    }

    @Override
    public Object getProperty(ExchangePropertyKey key) {
        throw new UnsupportedOperationException("Unimplemented method 'getProperty'");
    }

    @Override
    public <T> T getProperty(ExchangePropertyKey key, Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getProperty'");
    }

    @Override
    public <T> T getProperty(ExchangePropertyKey key, Object defaultValue, Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getProperty'");
    }

    @Override
    public void setProperty(ExchangePropertyKey key, Object value) {
        throw new UnsupportedOperationException("Unimplemented method 'setProperty'");
    }

    @Override
    public Object removeProperty(ExchangePropertyKey key) {
        throw new UnsupportedOperationException("Unimplemented method 'removeProperty'");
    }

    @Override
    public Object getProperty(String name) {
        return this.properties.get(name);
    }

    @Override
    public <T> T getProperty(String name, Class<T> type) {
        Object value = getProperty(name);
        if (value == null) {
            return null;
        }
        try {
            return type.cast(value);
        } catch (ClassCastException cce) {
            return null;
        }
    }

    @Override
    public <T> T getProperty(String name, Object defaultValue, Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getProperty'");
    }

    @Override
    public void setProperty(String name, Object value) {
        this.properties.put(name, value);
    }

    @Override
    public Object removeProperty(String name) {
        throw new UnsupportedOperationException("Unimplemented method 'removeProperty'");
    }

    @Override
    public boolean removeProperties(String pattern) {
        throw new UnsupportedOperationException("Unimplemented method 'removeProperties'");
    }

    @Override
    public boolean removeProperties(String pattern, String... excludePatterns) {
        throw new UnsupportedOperationException("Unimplemented method 'removeProperties'");
    }

    @Override
    public Map<String, Object> getProperties() {
        throw new UnsupportedOperationException("Unimplemented method 'getProperties'");
    }

    @Override
    public Map<String, Object> getAllProperties() {
        throw new UnsupportedOperationException("Unimplemented method 'getAllProperties'");
    }

    @Override
    public boolean hasProperties() {
        throw new UnsupportedOperationException("Unimplemented method 'hasProperties'");
    }

    @Override
    public Object getVariable(String name) {
        return this.variables.get(name);
    }

    @Override
    public <T> T getVariable(String name, Class<T> type) {
        Object value = getVariable(name);
        if (value == null) {
            return null;
        }
        try {
            return type.cast(value);
        } catch (ClassCastException cce) {
            return null;
        }
    }

    @Override
    public <T> T getVariable(String name, Object defaultValue, Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getVariable'");
    }

    @Override
    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }

    @Override
    public Object removeVariable(String name) {
        throw new UnsupportedOperationException("Unimplemented method 'removeVariable'");
    }

    @Override
    public Map<String, Object> getVariables() {
        throw new UnsupportedOperationException("Unimplemented method 'getVariables'");
    }

    @Override
    public boolean hasVariables() {
        throw new UnsupportedOperationException("Unimplemented method 'hasVariables'");
    }

    @Override
    public Message getIn() {
        throw new UnsupportedOperationException("Unimplemented method 'getIn'");
    }

    @Override
    public Message getMessage() {
        throw new UnsupportedOperationException("Unimplemented method 'getMessage'");
    }

    @Override
    public <T> T getMessage(Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getMessage'");
    }

    @Override
    public void setMessage(Message message) {
        throw new UnsupportedOperationException("Unimplemented method 'setMessage'");
    }

    @Override
    public <T> T getIn(Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getIn'");
    }

    @Override
    public void setIn(Message in) {
        throw new UnsupportedOperationException("Unimplemented method 'setIn'");
    }

    @Override
    @Deprecated
    public Message getOut() {
        throw new UnsupportedOperationException("Unimplemented method 'getOut'");
    }

    @Override
    @Deprecated
    public <T> T getOut(Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getOut'");
    }

    @Override
    @Deprecated
    public boolean hasOut() {
        throw new UnsupportedOperationException("Unimplemented method 'hasOut'");
    }

    @Override
    @Deprecated
    public void setOut(Message out) {
        throw new UnsupportedOperationException("Unimplemented method 'setOut'");
    }

    @Override
    public Exception getException() {
        throw new UnsupportedOperationException("Unimplemented method 'getException'");
    }

    @Override
    public <T> T getException(Class<T> type) {
        throw new UnsupportedOperationException("Unimplemented method 'getException'");
    }

    @Override
    public void setException(Throwable t) {
        throw new UnsupportedOperationException("Unimplemented method 'setException'");
    }

    @Override
    public boolean isFailed() {
        throw new UnsupportedOperationException("Unimplemented method 'isFailed'");
    }

    @Override
    public boolean isTransacted() {
        throw new UnsupportedOperationException("Unimplemented method 'isTransacted'");
    }

    @Override
    public boolean isRouteStop() {
        throw new UnsupportedOperationException("Unimplemented method 'isRouteStop'");
    }

    @Override
    public void setRouteStop(boolean routeStop) {
        throw new UnsupportedOperationException("Unimplemented method 'setRouteStop'");
    }

    @Override
    public boolean isExternalRedelivered() {
        throw new UnsupportedOperationException("Unimplemented method 'isExternalRedelivered'");
    }

    @Override
    public boolean isRollbackOnly() {
        throw new UnsupportedOperationException("Unimplemented method 'isRollbackOnly'");
    }

    @Override
    public void setRollbackOnly(boolean rollbackOnly) {
        throw new UnsupportedOperationException("Unimplemented method 'setRollbackOnly'");
    }

    @Override
    public boolean isRollbackOnlyLast() {
        throw new UnsupportedOperationException("Unimplemented method 'isRollbackOnlyLast'");
    }

    @Override
    public void setRollbackOnlyLast(boolean rollbackOnlyLast) {
        throw new UnsupportedOperationException("Unimplemented method 'setRollbackOnlyLast'");
    }

    @Override
    public CamelContext getContext() {
        throw new UnsupportedOperationException("Unimplemented method 'getContext'");
    }

    @Override
    public Exchange copy() {
        throw new UnsupportedOperationException("Unimplemented method 'copy'");
    }

    @Override
    public Endpoint getFromEndpoint() {
        throw new UnsupportedOperationException("Unimplemented method 'getFromEndpoint'");
    }

    @Override
    public String getFromRouteId() {
        throw new UnsupportedOperationException("Unimplemented method 'getFromRouteId'");
    }

    @Override
    public UnitOfWork getUnitOfWork() {
        throw new UnsupportedOperationException("Unimplemented method 'getUnitOfWork'");
    }

    @Override
    public String getExchangeId() {
        throw new UnsupportedOperationException("Unimplemented method 'getExchangeId'");
    }

    @Override
    public void setExchangeId(String id) {
        throw new UnsupportedOperationException("Unimplemented method 'setExchangeId'");
    }

    @Override
    @Deprecated
    public long getCreated() {
        throw new UnsupportedOperationException("Unimplemented method 'getCreated'");
    }

    @Override
    public ExchangeExtension getExchangeExtension() {
        throw new UnsupportedOperationException("Unimplemented method 'getExchangeExtension'");
    }

    @Override
    public Clock getClock() {
        throw new UnsupportedOperationException("Unimplemented method 'getClock'");
    }

}
