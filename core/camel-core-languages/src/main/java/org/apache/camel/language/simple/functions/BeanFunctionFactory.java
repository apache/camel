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
package org.apache.camel.language.simple.functions;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple function for bean invocation: {@code ${bean:ref}}, {@code ${bean:ref.method}},
 * {@code ${bean:ref::method}}, {@code ${bean:ref?method=m&scope=s}}, {@code ${bean:type:FQN}}.
 */
public final class BeanFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("bean:", function);
        if (remainder == null) {
            return null;
        }

        String[] parsed = parseBeanRemainder(remainder);
        String ref = parsed[0];
        String method = parsed[1];
        String scope = parsed[2];

        Class<?> type = null;
        if (ref != null && ref.startsWith("type:")) {
            try {
                type = camelContext.getClassResolver().resolveMandatoryClass(ref.substring(5));
                ref = null;
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        Language bean = camelContext.resolveLanguage("bean");
        Object[] properties = new Object[7];
        properties[2] = method;
        properties[3] = type;
        properties[4] = ref;
        properties[5] = scope;
        return bean.createExpression(null, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("bean:", function);
        if (remainder == null) {
            return null;
        }

        String[] parsed = parseBeanRemainder(remainder);
        String ref = parsed[0];
        String method = parsed[1];
        String scope = parsed[2];

        if (method != null && scope != null) {
            return "bean(exchange, bean, \"" + ref + "\", \"" + method + "\", \"" + scope + "\")";
        } else if (method != null) {
            return "bean(exchange, bean, \"" + ref + "\", \"" + method + "\", null)";
        } else if (scope != null) {
            return "bean(exchange, bean, \"" + ref + "\", null, \"" + scope + "\")";
        } else {
            return "bean(exchange, bean, \"" + ref + "\", null, null)";
        }
    }

    private static String[] parseBeanRemainder(String remainder) {
        String ref = remainder;
        String method = null;
        String scope = null;

        if (remainder.contains("?method=") || remainder.contains("?scope=")) {
            ref = StringHelper.before(remainder, "?");
            String query = StringHelper.after(remainder, "?");
            try {
                Map<String, Object> map = URISupport.parseQuery(query);
                Object m = map.get("method");
                Object s = map.get("scope");
                method = m != null ? m.toString() : null;
                scope = s != null ? s.toString() : null;
            } catch (URISyntaxException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        } else {
            // first check :: because of my.own.Bean::method
            int doubleColonIndex = remainder.indexOf("::");
            // need to check that not inside params
            int beginOfParameterDeclaration = remainder.indexOf('(');
            if (doubleColonIndex > 0 && (!remainder.contains("(") || doubleColonIndex < beginOfParameterDeclaration)) {
                ref = remainder.substring(0, doubleColonIndex);
                method = remainder.substring(doubleColonIndex + 2);
            } else {
                int idx = remainder.indexOf('.');
                if (idx > 0) {
                    ref = remainder.substring(0, idx);
                    method = remainder.substring(idx + 1);
                }
            }
        }

        return new String[] { ref.trim(), method, scope };
    }
}
