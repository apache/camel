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
package org.apache.camel.model;

import java.util.Comparator;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Sorts the contents of the message
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "sort")
@XmlAccessorType(XmlAccessType.FIELD)
public class SortDefinition<T> extends ExpressionNode {

    @XmlTransient
    private Comparator<? super T> comparatorBean;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.Comparator")
    private String comparator;

    public SortDefinition() {
    }

    public SortDefinition(Expression expression) {
        setExpression(ExpressionNodeHelper.toExpressionDefinition(expression));
    }

    public SortDefinition(Expression expression, Comparator<? super T> comparator) {
        this(expression);
        this.comparatorBean = comparator;
    }

    @Override
    public String toString() {
        return "sort[" + getExpression() + " by: " + (comparator != null ? "ref:" + comparator : comparatorBean) + "]";
    }

    @Override
    public String getShortName() {
        return "sort";
    }

    @Override
    public String getLabel() {
        return "sort[" + getExpression() + "]";
    }

    /**
     * Optional expression to sort by something else than the message body
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    /**
     * Sets the comparator to use for sorting
     *
     * @param  comparator the comparator to use for sorting
     * @return            the builder
     */
    public SortDefinition<T> comparator(Comparator<T> comparator) {
        this.comparatorBean = comparator;
        return this;
    }

    /**
     * Sets a reference to lookup for the comparator to use for sorting
     *
     * @param  ref reference for the comparator
     * @return     the builder
     */
    public SortDefinition<T> comparator(String ref) {
        setComparator(ref);
        return this;
    }

    public Comparator<? super T> getComparatorBean() {
        return comparatorBean;
    }

    public String getComparator() {
        return comparator;
    }

    public void setComparator(String comparator) {
        this.comparator = comparator;
    }
}
