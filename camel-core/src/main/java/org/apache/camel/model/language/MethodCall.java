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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.language.bean.BeanExpression;

/**
 * For expresions and predicates using the
 * <a href="http://activemq.apache.org/camel/bean-language.html>bean language</a>
 *
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "methodCall")
public class MethodCall extends ExpressionType {
    @XmlAttribute(required = false)
    private String bean;
    @XmlAttribute(required = false)
    private String method;

    public MethodCall() {
    }

    public MethodCall(String beanName) {
        super(beanName);
    }

    public MethodCall(String beanName, String method) {
        super(beanName);
        this.method = method;
    }

    public String getLanguage() {
        return "bean";
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public Expression createExpression(RouteContext routeContext) {
        return new BeanExpression(beanName(), getMethod());
    }

    @Override
    public Predicate<Exchange> createPredicate(RouteContext routeContext) {
        return new BeanExpression<Exchange>(beanName(), getMethod());
    }

    protected String beanName() {
        if (bean != null) {
            return bean;
        }
        return getExpression();
    }
}