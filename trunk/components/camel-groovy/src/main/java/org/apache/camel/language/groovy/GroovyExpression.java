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
package org.apache.camel.language.groovy;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version 
 */
public class GroovyExpression extends ExpressionSupport {
    private final String text;

    public GroovyExpression(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "groovy: " + text;
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return "groovy: " + text;
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        // use application classloader if given
        ClassLoader cl = exchange.getContext().getApplicationContextClassLoader();
        GroovyShell shell = cl != null ? new GroovyShell(cl) : new GroovyShell();

        // need to re-parse script due thread-safe with binding
        Script script = shell.parse(text);
        configure(exchange, script.getBinding());
        Object value = script.evaluate(text);

        return exchange.getContext().getTypeConverter().convertTo(type, value);
    }

    private void configure(Exchange exchange, final Binding binding) {
        ExchangeHelper.populateVariableMap(exchange, new AbstractMap<String, Object>() {
            @Override
            public Object put(String key, Object value) {
                binding.setProperty(key, value);
                return null;
            }

            @Override
            public Set<Entry<String, Object>> entrySet() {
                return Collections.emptySet();
            }
        });
    }
}
