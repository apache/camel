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
package org.apache.camel.language.bean;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.impl.ExpressionSupport;

/**
 * Evaluates an expression using a bean method invocation
 *
 * @version $Revision$
 */
public class BeanExpression<E extends Exchange> extends ExpressionSupport<E> {
    private String beanName;
    private String method;

    public BeanExpression(String beanName, String method) {
        this.beanName = beanName;
        this.method = method;
    }

    @Override
    public String toString() {
        return "BeanExpression[bean: " + beanName + " method: " + method + "]";
    }

    protected String assertionFailureMessage(E exchange) {
        return "bean: " + beanName + " method: " + method;
    }

    public Object evaluate(E exchange) {
        BeanProcessor processor = new BeanProcessor(new RegistryBean(exchange.getContext(), beanName));
        if (method != null) {
            processor.setMethod(method);
        }
        try {
            Exchange newExchange = exchange.copy();
            // The BeanExperession always has a result regardless of the ExchangePattern,
            // so I add a checker here to make sure we can get the result.
            if (!newExchange.getPattern().isOutCapable()) {
                newExchange.setPattern(ExchangePattern.InOut);
            }
            processor.process(newExchange);
            return newExchange.getOut(true).getBody();
        } catch (Exception e) {
            throw new RuntimeBeanExpressionException(exchange, beanName, method, e);
        }
    }
}
