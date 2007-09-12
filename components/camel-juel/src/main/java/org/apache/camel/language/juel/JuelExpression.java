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
package org.apache.camel.language.juel;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import de.odysseus.el.util.SimpleContext;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.ExpressionSupport;

/**
 * The <a href="http://activemq.apache.org/camel/el.html">EL Language from JSP and JSF</a>
 * using the <a href="http://activemq.apache.org/camel/juel.html">JUEL library</a>
 * 
 * @version $Revision: $
 */
public class JuelExpression extends ExpressionSupport<Exchange> {

    private final String expression;
    private final Class<?> type;
    private ExpressionFactory expressionFactory;

    public JuelExpression(String expression, Class<?> type) {
        this.expression = expression;
        this.type = type;
    }

    public static JuelExpression el(String expression) {
        return new JuelExpression(expression, Object.class);
    }

    public Object evaluate(Exchange exchange) {
        // TODO we could use caching here but then we'd have possible concurrency issues
        // so lets assume that the provider caches
        ELContext context = populateContext(createContext(), exchange);
        ValueExpression valueExpression = getExpressionFactory().createValueExpression(context, expression, type);
        return valueExpression.getValue(context);
    }

    public ExpressionFactory getExpressionFactory() {
        if (expressionFactory == null) {
            expressionFactory = ExpressionFactory.newInstance();
        }
        return expressionFactory;
    }

    public void setExpressionFactory(ExpressionFactory expressionFactory) {
        this.expressionFactory = expressionFactory;
    }

    protected ELContext populateContext(ELContext context, Exchange exchange) {
        setVariable(context, "exchange", exchange, Exchange.class);
        setVariable(context, "in", exchange.getIn(), Message.class);
        setVariable(context, "out", exchange.getOut(), Message.class);
        return context;
    }

    protected void setVariable(ELContext context, String name, Object value, Class<?> type) {
        ValueExpression valueExpression = getExpressionFactory().createValueExpression(value, type);
        SimpleContext simpleContext = (SimpleContext) context;
        simpleContext.setVariable(name, valueExpression);
    }

    /**
     * Factory method to create the EL context
     */
    protected ELContext createContext() {
        return new SimpleContext();
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return expression;
    }
}
