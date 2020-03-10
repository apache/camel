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
package org.apache.camel.reifier;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.reifier.language.ExpressionReifier;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public abstract class AbstractReifier implements BeanRepository {

    protected final org.apache.camel.Route route;
    protected final CamelContext camelContext;

    public AbstractReifier(Route route) {
        this.route = ObjectHelper.notNull(route, "Route");
        this.camelContext = route.getCamelContext();
    }

    public AbstractReifier(CamelContext camelContext) {
        this.route = null;
        this.camelContext = ObjectHelper.notNull(camelContext, "CamelContext");
    }

    protected String parseString(String text) {
        return CamelContextHelper.parseText(camelContext, text);
    }

    protected Boolean parseBoolean(String text) {
        return CamelContextHelper.parseBoolean(camelContext, text);
    }

    protected boolean parseBoolean(String text, boolean def) {
        Boolean b = parseBoolean(text);
        return b != null ? b : def;
    }

    protected Long parseLong(String text) {
        return CamelContextHelper.parseLong(camelContext, text);
    }

    protected long parseLong(String text, long def) {
        Long l = parseLong(text);
        return l != null ? l : def;
    }

    protected Integer parseInt(String text) {
        return CamelContextHelper.parseInteger(camelContext, text);
    }

    protected int parseInt(String text, int def) {
        Integer i = parseInt(text);
        return i != null ? i : def;
    }

    protected Float parseFloat(String text) {
        return CamelContextHelper.parseFloat(camelContext, text);
    }

    protected float parseFloat(String text, float def) {
        Float f = parseFloat(text);
        return f != null ? f : def;
    }

    protected <T> T parse(Class<T> clazz, String text) {
        return CamelContextHelper.parse(camelContext, clazz, text);
    }

    protected <T> T parse(Class<T> clazz, Object text) {
        if (text instanceof String) {
            text = parseString((String) text);
        }
        return CamelContextHelper.convertTo(camelContext, clazz, text);
    }

    protected Expression createExpression(ExpressionDefinition expression) {
        return ExpressionReifier.reifier(camelContext, expression).createExpression();
    }

    protected Expression createExpression(ExpressionSubElementDefinition expression) {
        return ExpressionReifier.reifier(camelContext, expression).createExpression();
    }

    protected Predicate createPredicate(ExpressionDefinition expression) {
        return ExpressionReifier.reifier(camelContext, expression).createPredicate();
    }

    protected Predicate createPredicate(ExpressionSubElementDefinition expression) {
        return ExpressionReifier.reifier(camelContext, expression).createPredicate();
    }

    protected Object or(Object a, Object b) {
        return a != null ? a : b;
    }

    protected Object asRef(String s) {
        return s != null ? s.startsWith("#") ? s : "#" + s : null;
    }

    protected BeanRepository getRegistry() {
        return camelContext.getRegistry();
    }

    public <T> T mandatoryLookup(String name, Class<T> beanType) {
        return CamelContextHelper.mandatoryLookup(camelContext, name, beanType);
    }

    public <T> T findSingleByType(Class<T> type) {
        return CamelContextHelper.findByType(camelContext, type);
    }

    @Override
    public Object lookupByName(String name) {
        return getRegistry().lookupByName(name);
    }

    public <T> T lookup(String name, Class<T> type) {
        return lookupByNameAndType(name, type);
    }

    public <T> T lookupByNameAndType(String name, Class<T> type) {
        return getRegistry().lookupByNameAndType(name, type);
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        return getRegistry().findByTypeWithName(type);
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        return getRegistry().findByType(type);
    }

    @Override
    public Object unwrap(Object value) {
        return getRegistry().unwrap(value);
    }

    public Endpoint resolveEndpoint(String uri) throws NoSuchEndpointException {
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri);
    }

}
