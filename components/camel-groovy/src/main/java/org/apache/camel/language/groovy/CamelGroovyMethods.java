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

import groovy.lang.Closure;
import org.apache.camel.Exchange;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.support.ExpressionSupport;

/**
 * @version 
 * @deprecated see {@link org.apache.camel.groovy.extend.CamelGroovyMethods} which is
 * used automatically
 */
@Deprecated
public final class CamelGroovyMethods {
    private CamelGroovyMethods() {
        // Utility Class
    }

    public static FilterDefinition filter(ProcessorDefinition<?> self, Closure<?> filter) {
        return self.filter(toExpression(filter));
    }

    public static ChoiceDefinition when(ChoiceDefinition self, Closure<?> filter) {
        return self.when(toExpression(filter));
    }

    public static ExpressionSupport toExpression(final Closure<?> filter) {
        return new ExpressionSupport() {
            protected String assertionFailureMessage(Exchange exchange) {
                return filter.toString();
            }

            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Object result = filter.call(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, result);
            }

            @Override
            public String toString() {
                return "Groovy[" + filter + "]";
            }
        };
    }

}
