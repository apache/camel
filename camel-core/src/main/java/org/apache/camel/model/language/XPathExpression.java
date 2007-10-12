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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.impl.RouteContext;
import org.w3c.dom.Element;

/**
 * For XPath expresions and predicates
 *
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "xpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class XPathExpression extends ExpressionType {
    @XmlTransient
    private Element element;

    public XPathExpression() {
    }

    public XPathExpression(String expression) {
        super(expression);
    }

    public String getLanguage() {
        return "xpath";
    }

    public Element getElement() {
        return element;
    }

    /**
     * Sets the XML element in which this XPath node is defined so that
     * the namespace context can be reused by the XPath expression
     *
     * @param element the XML element node which defines this xpath expression
     */
    public void setElement(Element element) {
        this.element = element;
    }

    @Override
    protected void configureExpresion(RouteContext routeContext, Expression expression) {
        configureXPathBuilder(expression);
    }

    @Override
    protected void configurePredicate(RouteContext routeContext, Predicate predicate) {
        configureXPathBuilder(predicate);
    }

    protected void configureXPathBuilder(Object builder) {
        if (element != null && builder instanceof XPathBuilder) {
            XPathBuilder xPathBuilder = (XPathBuilder) builder;
            xPathBuilder.setNamespacesFromDom(element);
        }
    }
}