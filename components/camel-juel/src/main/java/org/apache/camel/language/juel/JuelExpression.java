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
import java.util.Properties;

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
import org.apache.camel.impl.ExpressionSupport;
import org.apache.camel.spi.FactoryFinder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The <a href="http://camel.apache.org/el.html">EL Language from JSP and JSF</a>
 * using the <a href="http://camel.apache.org/juel.html">JUEL library</a>
 *
 * @version $Revision$
 */
public class JuelExpression extends ExpressionSupport {
    public static final String DEFAULT_EXPRESSION_FACTORY_IMPL_CLASS = "de.odysseus.el.ExpressionFactoryImpl";
    private static final Log LOG = LogFactory.getLog(JuelExpression.class);

    private final String expression;
    private final Class<?> type;
    private ExpressionFactory expressionFactory;
    private Properties expressionFactoryProperties;

    public JuelExpression(String expression, Class<?> type) {
        this.expression = expression;
        this.type = type;
    }

    public static JuelExpression el(String expression) {
        return new JuelExpression(expression, Object.class);
    }

    public <T> T evaluate(Exchange exchange, Class<T> tClass) {
        // TODO we could use caching here but then we'd have possible concurrency issues
        // so lets assume that the provider caches
        ELContext context = populateContext(createContext(), exchange);
        ValueExpression valueExpression = getExpressionFactory(exchange.getContext()).createValueExpression(context, expression, type);
        Object value = valueExpression.getValue(context);
        return exchange.getContext().getTypeConverter().convertTo(tClass, value);
    }

    public ExpressionFactory getExpressionFactory(CamelContext context) {
        if (expressionFactory == null && context != null) {
            try {
                FactoryFinder finder = context.getFactoryFinder("META-INF/services/org/apache/camel/component/");
                Class<?> clazz = finder.findClass("juel", "impl.");
                if (clazz != null) {
                    expressionFactory = (ExpressionFactory)clazz.newInstance();
                }
            } catch (ClassNotFoundException e) {
                LOG.debug("'impl.class' not found", e);
            } catch (IOException e) {
                LOG.debug("No impl class for juel ExpressionFactory defined in 'META-INF/services/org/apache/camel/component/el'", e);
            } catch (InstantiationException e) {
                LOG.debug("Failed to instantiate juel ExpressionFactory implementation class.", e);
            } catch (IllegalAccessException e) {
                LOG.debug("Failed to instantiate juel ExpressionFactory implementation class.", e);
            }
        }
        return getExpressionFactory();
    }

    public ExpressionFactory getExpressionFactory() {
        if (expressionFactory == null) {
            Properties properties = getExpressionFactoryProperties();
            expressionFactory = new ExpressionFactoryImpl(properties);
        }
        return expressionFactory;
    }

    public void setExpressionFactory(ExpressionFactory expressionFactory) {
        this.expressionFactory = expressionFactory;
    }

    public Properties getExpressionFactoryProperties() {
        if (expressionFactoryProperties == null) {
            expressionFactoryProperties = new Properties();
            populateDefaultExpressionProperties(expressionFactoryProperties);
        }
        return expressionFactoryProperties;
    }

    public void setExpressionFactoryProperties(Properties expressionFactoryProperties) {
        this.expressionFactoryProperties = expressionFactoryProperties;
    }

    protected ELContext populateContext(ELContext context, Exchange exchange) {
        setVariable(context, "exchange", exchange, Exchange.class);
        setVariable(context, "in", exchange.getIn(), Message.class);
        if (exchange.hasOut()) {
            setVariable(context, "out", exchange.getOut(), Message.class);
        }
        return context;
    }

    /**
     * A Strategy Method to populate the default properties used to create the expression factory
     */
    protected void populateDefaultExpressionProperties(Properties properties) {
        // lets enable method invocations
        properties.setProperty("javax.el.methodInvocations", "true");
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
                //add(methodResolver);
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
