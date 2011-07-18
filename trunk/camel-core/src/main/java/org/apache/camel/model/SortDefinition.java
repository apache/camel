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

import java.util.Comparator;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.SortProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.builder.ExpressionBuilder.bodyExpression;

/**
 * Represents an XML &lt;sort/&gt; element
 */
@XmlRootElement(name = "sort")
@XmlAccessorType(XmlAccessType.FIELD)
public class SortDefinition extends NoOutputExpressionNode {
    @XmlTransient
    private Comparator<Object> comparator;
    @XmlAttribute
    private String comparatorRef;

    public SortDefinition() {
    }

    public SortDefinition(Expression expression) {
        setExpression(new ExpressionDefinition(expression));
    }

    public SortDefinition(Expression expression, Comparator<Object> comparator) {
        this(expression);
        this.comparator = comparator;
    }

    @Override
    public String toString() {
        return "sort[" + getExpression() + " by: " + (comparatorRef != null ? "ref:" + comparatorRef : comparator) + "]";
    }

    @Override
    public String getShortName() {
        return "sort";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // lookup in registry
        if (ObjectHelper.isNotEmpty(comparatorRef)) {
            comparator = routeContext.getCamelContext().getRegistry().lookup(comparatorRef, Comparator.class);
        }

        // if no comparator then default on to string representation
        if (comparator == null) {
            comparator = new Comparator<Object>() {
                public int compare(Object o1, Object o2) {
                    return ObjectHelper.compare(o1, o2);
                }
            };
        }

        // if no expression provided then default to body expression
        Expression exp;
        if (getExpression() == null) {
            exp = bodyExpression();
        } else {
            exp = getExpression().createExpression(routeContext);
        }
        return new SortProcessor(exp, getComparator());
    }

    public Comparator<Object> getComparator() {
        return comparator;
    }

    public void setComparator(Comparator<Object> comparator) {
        this.comparator = comparator;
    }

    public String getComparatorRef() {
        return comparatorRef;
    }

    public void setComparatorRef(String comparatorRef) {
        this.comparatorRef = comparatorRef;
    }

    /**
     * Sets the comparator to use for sorting
     *
     * @param comparator  the comparator to use for sorting
     * @return the builder
     */
    public SortDefinition comparator(Comparator<Object> comparator) {
        setComparator(comparator);
        return this;
    }

    /**
     * Sets a reference to lookup for the comparator to use for sorting
     *
     * @param ref reference for the comparator
     * @return the builder
     */
    public SortDefinition comparatorRef(String ref) {
        setComparatorRef(ref);
        return this;
    }
}
