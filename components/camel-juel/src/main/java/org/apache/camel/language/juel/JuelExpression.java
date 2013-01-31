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

import java.io.IOException;

import javax.el.ArrayELResolver;
import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.ListELResolver;
import javax.el.MapELResolver;
import javax.el.ResourceBundleELResolver;
import javax.el.ValueExpression;
import de.odysseus.el.ExpressionFactoryImpl;
import de.odysseus.el.util.SimpleContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.ExpressionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/el.html">EL Language from JSP and JSF</a>
 * using the <a href="http://camel.apache.org/juel.html">JUEL library</a>
 *
 * @version 
 */
public class JuelExpression extends ExpressionSupport {
    public static final String DEFAULT_EXPRESSION_FACTORY_IMPL_CLASS = "de.odysseus.el.ExpressionFactoryImpl";
    private static final Logger LOG = LoggerFactory.getLogger(JuelExpression.class);

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

    public <T> T evaluate(Exchange exchange, Class<T> tClass) {
        // Create (if needed) the ExpressionFactory first from the CamelContext using FactoryFinder
        ExpressionFactory factory = getExpressionFactory(exchange.getContext());
        ELContext context = populateContext(createContext(), exchange);
        ValueExpression valueExpression = factory.createValueExpression(context, expression, type);
        Object value = valueExpression.getValue(context);
        LOG.trace("Value returned {}", value);
        return exchange.getContext().getTypeConverter().convertTo(tClass, value);
    }

    public synchronized ExpressionFactory getExpressionFactory(CamelContext context) {
        if (expressionFactory == null && context != null) {
            try {
                FactoryFinder finder = context.getFactoryFinder("META-INF/services/org/apache/camel/language/");
                Class<?> clazz = finder.findClass("el", "impl.", ExpressionFactory.class);
                if (clazz != null) {
                    expressionFactory = (ExpressionFactory)clazz.newInstance();
                }
            } catch (ClassNotFoundException e) {
                LOG.debug("'impl.class' not found", e);
            } catch (IOException e) {
                LOG.debug("No impl class for juel ExpressionFactory defined in 'META-INF/services/org/apache/camel/language/el'", e);
            } catch (InstantiationException e) {
                LOG.debug("Failed to instantiate juel ExpressionFactory implementation class.", e);
            } catch (IllegalAccessException e) {
                LOG.debug("Failed to instantiate juel ExpressionFactory implementation class.", e);
            }
        }
        return getExpressionFactory();
    }

    public synchronized ExpressionFactory getExpressionFactory() {
        if (expressionFactory == null) {
            expressionFactory = new ExpressionFactoryImpl();
        }
        return expressionFactory;
    }

    public void setExpressionFactory(ExpressionFactory expressionFactory) {
        this.expressionFactory = expressionFactory;
    }

    protected ELContext populateContext(ELContext context, Exchange exchange) {
        setVariable(context, "exchange", exchange, Exchange.class);
        setVariable(context, "in", exchange.getIn(), Message.class);
        if (exchange.hasOut()) {
            setVariable(context, "out", exchange.getOut(), Message.class);
        }
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
        ELResolver resolver = new CompositeELResolver() {
            {
                add(new ArrayELResolver(false));
                add(new ListELResolver(false));
                add(new MapELResolver(false));
                add(new ResourceBundleELResolver());
                add(new BeanAndMethodELResolver());
            }
        };
        return new SimpleContext(resolver);
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return expression;
    }
}
