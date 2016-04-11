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

import java.io.InputStream;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Language producer.
 *
 * @version 
 */
public class LanguageProducer extends DefaultProducer {

    public LanguageProducer(LanguageEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        String script = null;

        // is there a custom expression in the header?
        Expression exp = exchange.getIn().getHeader(Exchange.LANGUAGE_SCRIPT, Expression.class);
        if (exp == null) {
            script = exchange.getIn().getHeader(Exchange.LANGUAGE_SCRIPT, String.class);
            if (script != null) {
                // the script may be a file: so resolve it before using
                script = getEndpoint().resolveScript(script);
                exp = getEndpoint().getLanguage().createExpression(script);
            }
        }
        // if not fallback to use expression from endpoint
        if (exp == null && getEndpoint().isCacheScript()) {
            exp = getEndpoint().getExpression();
        }

        // the script can be a resource from the endpoint,
        // or refer to a resource itself
        // or just be a plain string
        InputStream is = null;

        // fallback and use resource uri from endpoint
        if (exp == null) {
            script = getEndpoint().getScript();

            if (script == null && getEndpoint().getResourceUri() == null) {
                // no script to execute
                throw new CamelExchangeException("No script to evaluate", exchange);
            }

            if (script == null) {
                is = getEndpoint().getResourceAsInputStream();
            } else if (ResourceHelper.hasScheme(script)) {
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(getEndpoint().getCamelContext(), script);
            }

            if (is != null && !getEndpoint().isBinary()) {
                try {
                    script = getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, exchange, is);
                } finally {
                    IOHelper.close(is);
                }
            }
        }

        // if we have a text based script then use and evaluate it
        if (script != null) {
            // create the expression from the script
            exp = getEndpoint().getLanguage().createExpression(script);
            // expression was resolved from resource
            getEndpoint().setContentResolvedFromResource(true);
            // if we cache then set this as expression on endpoint so we don't re-create it again
            if (getEndpoint().isCacheScript()) {
                getEndpoint().setExpression(exp);
            }
        }

        // the result is either the result of the expression or the input stream as-is because its binary content
        Object result;
        if (exp != null) {
            try {
                result = exp.evaluate(exchange, Object.class);
                log.debug("Evaluated expression as: {} with: {}", result, exchange);
            } finally {
                if (!getEndpoint().isCacheScript()) {
                    // some languages add themselves as a service which we then need to remove if we are not cached
                    ServiceHelper.stopService(exp);
                    getEndpoint().getCamelContext().removeService(exp);
                }
            }
        } else {
            // use the result as-is
            result = is;
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
