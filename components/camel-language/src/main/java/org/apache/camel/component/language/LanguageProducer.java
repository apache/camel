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
package org.apache.camel.component.language;

import java.io.InputStream;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Language producer.
 */
public class LanguageProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LanguageProducer.class);

    private Class<?> resultType = Object.class;

    public LanguageProducer(LanguageEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (getEndpoint().isAllowTemplateFromHeader() && processHeaderScript(exchange)) {
            return;
        }

        String script = null;
        Expression exp = null;

        // use the cached expression from endpoint
        if (getEndpoint().isCacheScript()) {
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
            } else if (EndpointHelper.isReferenceParameter(script)) {
                String ref = "ref:" + script.substring(1);
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(getEndpoint().getCamelContext(), ref);
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
            Class<?> type = resultType != Object.class ? resultType : null;
            exp = getEndpoint().getLanguage().createExpression(script, new Object[] { type });
            exp.init(getEndpoint().getCamelContext());
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
            result = evaluate(exchange, exp, !getEndpoint().isCacheScript());
        } else {
            // use the result as-is
            result = is;
        }

        // set message body if transform is enabled
        if (getEndpoint().isTransform()) {
            exchange.getMessage().setBody(result);
        }
    }

    /**
     * Evaluates the script of the {@link LanguageConstants#LANGUAGE_SCRIPT} header, if there is one. The script is for
     * this exchange only: it is compiled once, never cached as the script of the endpoint, and the header is removed so
     * a later language endpoint does not run it again.
     *
     * @return true if the header had a script, false to use the script of the endpoint
     */
    private boolean processHeaderScript(Exchange exchange) throws Exception {
        Expression exp = exchange.getIn().getHeader(LanguageConstants.LANGUAGE_SCRIPT, Expression.class);
        boolean compiled = false;
        if (exp == null) {
            String script = exchange.getIn().getHeader(LanguageConstants.LANGUAGE_SCRIPT, String.class);
            if (script == null) {
                return false;
            }
            // the script may be a file: so resolve it before using
            script = getEndpoint().resolveScript(script);
            Class<?> type = resultType != Object.class ? resultType : null;
            exp = getEndpoint().getLanguage().createExpression(script, new Object[] { type });
            exp.init(getEndpoint().getCamelContext());
            compiled = true;
        }
        exchange.getIn().removeHeader(LanguageConstants.LANGUAGE_SCRIPT);

        // the expression compiled from the script is used once, so it is always stopped after use
        Object result = evaluate(exchange, exp, compiled || !getEndpoint().isCacheScript());
        if (getEndpoint().isTransform()) {
            exchange.getMessage().setBody(result);
        }
        return true;
    }

    private Object evaluate(Exchange exchange, Expression exp, boolean stopAfterUse) throws Exception {
        try {
            Object result = exp.evaluate(exchange, resultType);
            LOG.debug("Evaluated expression as: {} with: {}", result, exchange);
            return result;
        } finally {
            if (stopAfterUse) {
                // some languages add themselves as a service which we then need to remove if we are not cached
                ServiceHelper.stopService(exp);
                getEndpoint().getCamelContext().removeService(exp);
            }
        }
    }

    @Override
    public LanguageEndpoint getEndpoint() {
        return (LanguageEndpoint) super.getEndpoint();
    }

    @Override
    protected void doBuild() throws Exception {
        if (getEndpoint().getResultType() != null) {
            resultType = getEndpoint().getCamelContext()
                    .getClassResolver().resolveMandatoryClass(getEndpoint().getResultType());
        }
    }
}
