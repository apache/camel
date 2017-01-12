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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Predicate to determine if the message should be sent or not to the endpoint, when using interceptSentToEndpoint.
 */
@Metadata(label = "configuration") @AsPredicate
@XmlRootElement(name = "whenSkipSendToEndpoint")
public class WhenSkipSendToEndpointDefinition extends WhenDefinition {

    @Override
    protected Predicate createPredicate(RouteContext routeContext) {
        // we need to keep track whether the when matches or not, so delegate
        // the predicate and add the matches result as a property on the exchange
        final Predicate delegate = super.createPredicate(routeContext);
        return new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                boolean matches = delegate.matches(exchange);
                exchange.setProperty(Exchange.INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED, matches);
                return matches;
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }

    /**
     * Expression used as the predicate to evaluate whether the message should be sent or not to the endpoint
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

}
