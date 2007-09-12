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
package org.apache.camel.groovy;

import groovy.lang.Closure;
import org.apache.camel.Endpoint;
import org.apache.camel.Predicate;
import org.apache.camel.Exchange;
import org.apache.camel.impl.ExpressionSupport;
import org.apache.camel.model.FilterType;
import org.apache.camel.model.RouteType;

/**
 * Adds some groovy helper methods
 *
 * @version $Revision: 1.1 $
 */
public class GroovyRouteType extends RouteType {
    public GroovyRouteType() {
    }

    public GroovyRouteType(Endpoint endpoint) {
        super(endpoint);
    }

    public GroovyRouteType(String uri) {
        super(uri);
    }

    public FilterType filter(Closure filter) {
        return super.filter(asPredicate(filter));
    }

    protected Predicate asPredicate(final Closure filter) {
        return new ExpressionSupport<Exchange>() {
            protected String assertionFailureMessage(Exchange exchange) {
                return filter.toString();
            }

            public Object evaluate(Exchange exchange) {
                return filter.call(exchange);
            }

            @Override
            public String toString() {
                return "Groovy[" + filter + "]";
            }
        };
    }
}
