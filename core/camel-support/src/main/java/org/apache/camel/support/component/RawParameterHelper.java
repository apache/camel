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
package org.apache.camel.support.component;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.URISupport;

public final class RawParameterHelper {

    private RawParameterHelper() {
    }

    /**
     * Traverses the given parameters, and resolve any parameter values which uses the RAW token syntax:
     * <tt>key=RAW(value)</tt>. This method will then remove the RAW tokens, and replace the content of the value, with
     * just the value.
     *
     * The value can refer to a simple expression using $simple{xxx} syntax which allows to refer to passwords defined
     * as ENV variables etc.
     *
     * When the endpoint currently being created on this thread binds its option values as literal text (see
     * {@link PropertyBindingSupport#isLiteralOptionBinding()}), the RAW tokens are removed but a $simple{xxx} in the
     * value is not evaluated, see {@link #resolveRawParameterValues(CamelContext, Map, boolean)}.
     *
     * @param camelContext the camel context
     * @param parameters   the uri parameters
     */
    public static void resolveRawParameterValues(CamelContext camelContext, Map<String, Object> parameters) {
        resolveRawParameterValues(camelContext, parameters, PropertyBindingSupport.isLiteralOptionBinding());
    }

    /**
     * Traverses the given parameters, and resolves any parameter values which use the RAW token syntax:
     * <code>key=RAW(value)</code>. This method will then remove the RAW tokens, and replace the content of the value,
     * with just the value.
     *
     * When {@code literal} is {@code false} the value can refer to a simple expression using $simple{xxx} syntax which
     * allows to refer to passwords defined as ENV variables etc. When {@code literal} is {@code true} the value is
     * taken as literal text and a $simple{xxx} in it is not evaluated. This is used for an endpoint whose uri was
     * computed from the current message at runtime (for example the per-message recipient of {@code toD} or
     * {@code pollEnrich}), as its option values come from the message and not from the route.
     *
     * @param camelContext the camel context
     * @param parameters   the uri parameters
     * @param literal      whether the RAW values are taken as literal text, without evaluating $simple{xxx}
     * @since              4.24
     */
    public static void resolveRawParameterValues(
            CamelContext camelContext, Map<String, Object> parameters, boolean literal) {
        URISupport.resolveRawParameterValues(parameters, s -> {
            if (!literal && s != null && s.contains("$simple{")) {
                Exchange dummy = ExchangeHelper.getDummy(camelContext);
                s = camelContext.resolveLanguage("simple").createExpression(s).evaluate(dummy, String.class);
            }
            return s;
        });
    }
}
