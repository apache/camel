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
package org.apache.camel.component.language;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * Language producer.
 *
 * @version $Revision$
 */
public class LanguageProducer extends DefaultProducer {

    public LanguageProducer(LanguageEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        // is there a custom expression in the header?
        Expression exp = exchange.getIn().getHeader(Exchange.LANGUAGE_SCRIPT, Expression.class);
        if (exp == null) {
            String script = exchange.getIn().getHeader(Exchange.LANGUAGE_SCRIPT, String.class);
            if (script != null) {
                exp = getEndpoint().getLanguage().createExpression(script);
            }
        }
        // if not fallback to use expression from endpoint
        if (exp == null) {
            exp = getEndpoint().getExpression();
        }

        ObjectHelper.notNull(exp, "expression");

        Object result = exp.evaluate(exchange, Object.class);
        if (log.isDebugEnabled()) {
            log.debug("Evaluated expression as: " + result + " with: " + exchange);
        }

        // set message body if transform is enabled
        if (getEndpoint().isTransform()) {
            if (exchange.hasOut()) {
                exchange.getOut().setBody(result);
            } else {
                exchange.getIn().setBody(result);
            }
        }
    }

    @Override
    public LanguageEndpoint getEndpoint() {
        return (LanguageEndpoint) super.getEndpoint();
    }
}
