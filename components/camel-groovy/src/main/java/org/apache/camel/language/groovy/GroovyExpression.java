/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import groovy.lang.Script;
import org.apache.camel.Exchange;
import org.apache.camel.impl.ExpressionSupport;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version $Revision: 1.1 $
 */
public class GroovyExpression extends ExpressionSupport<Exchange> {
    private Class<Script> scriptType;
    private String text;

    public GroovyExpression(Class<Script> scriptType, String text) {
        this.scriptType = scriptType;
        this.text = text;
    }

    @Override
    public String toString() {
        return "groovy: " + text;
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return "groovy: " + text;
    }

    public Object evaluate(Exchange exchange) {
        Script script = ExchangeHelper.newInstance(exchange, scriptType);
        // lets configure the script
        configure(exchange, script);
        return script.run();
    }

    private void configure(Exchange exchange, Script script) {
        final Binding binding = script.getBinding();
        ExchangeHelper.populateVariableMap(exchange, new AbstractMap<String, Object>() {
            @Override
            public Object put(String key, Object value) {
                binding.setProperty(key, value);
                return null;
            }

            public Set entrySet() {
                return Collections.EMPTY_SET;
            }
        });
    }
}
