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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.Constants;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.OtherAttributesAware;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.reifier.language.ExpressionReifier;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

public abstract class AbstractReifier {

    private static final String PREFIX = "{" + Constants.PLACEHOLDER_QNAME + "}";

    protected final RouteContext routeContext;
    protected final CamelContext camelContext;

    public AbstractReifier(RouteContext routeContext) {
        this.routeContext = routeContext;
        this.camelContext = routeContext.getCamelContext();
    }

    public AbstractReifier(CamelContext camelContext) {
        this.routeContext = null;
        this.camelContext = camelContext;
    }

    protected String parseString(String text) {
        return CamelContextHelper.parseText(camelContext, text);
    }

    protected boolean parseBoolean(String text) {
        Boolean b = CamelContextHelper.parseBoolean(camelContext, text);
        return b != null && b;
    }

    protected Long parseLong(String text) {
        return CamelContextHelper.parseLong(camelContext, text);
    }

    protected Integer parseInt(String text) {
        return CamelContextHelper.parseInteger(camelContext, text);
    }

    protected <T> T parse(Class<T> clazz, String text) {
        return CamelContextHelper.parse(camelContext, clazz, text);
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

    @SuppressWarnings("unchecked")
    protected void addOtherAttributes(Object definition, Map<String, Object> properties) {
        if (definition instanceof OtherAttributesAware) {
            Map<Object, Object> others = ((OtherAttributesAware) definition).getOtherAttributes();
            if (others != null) {
                others.forEach((k, v) -> {
                    String ks = k.toString();
                    if (ks.startsWith(PREFIX) && v instanceof String) {
                        // value must be enclosed with placeholder tokens
                        String s = (String) v;
                        if (!s.startsWith(PropertiesComponent.PREFIX_TOKEN) && !s.endsWith(PropertiesComponent.SUFFIX_TOKEN)) {
                            s = PropertiesComponent.PREFIX_TOKEN + s + PropertiesComponent.SUFFIX_TOKEN;
                        }
                        String kk = ks.substring(PREFIX.length());
                        properties.put(kk, s);
                    }
                });
            }
        }
    }

    protected Object or(Object a, Object b) {
        return a != null ? a : b;
    }

    protected Object asRef(String s) {
        return s != null ? s.startsWith("#") ? s : "#" + s : null;
    }

}
