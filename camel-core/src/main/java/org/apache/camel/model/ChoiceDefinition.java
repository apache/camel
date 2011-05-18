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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * Represents an XML &lt;choice/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "choice")
@XmlAccessorType(XmlAccessType.FIELD)
public class ChoiceDefinition extends ProcessorDefinition<ChoiceDefinition> {
    @XmlElementRef
    private List<WhenDefinition> whenClauses = new ArrayList<WhenDefinition>();
    @XmlElement
    private OtherwiseDefinition otherwise;

    public ChoiceDefinition() {
    }

    @Override
    public String toString() {
        if (getOtherwise() != null) {
            return "Choice[" + getWhenClauses() + " " + getOtherwise() + "]";
        } else {
            return "Choice[" + getWhenClauses() + "]";

        }
    }
    @Override
    public String getShortName() {
        return "choice";
    }
           
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        List<FilterProcessor> filters = new ArrayList<FilterProcessor>();
        for (WhenDefinition whenClaus : whenClauses) {
            filters.add(whenClaus.createProcessor(routeContext));
        }
        Processor otherwiseProcessor = null;
        if (otherwise != null) {
            otherwiseProcessor = otherwise.createProcessor(routeContext);
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

    // Fluent API
    // -------------------------------------------------------------------------
    /**
     * Sets the predicate for the when node
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public ChoiceDefinition when(Predicate predicate) {
        WhenDefinition when = new WhenDefinition(predicate);
        when.setParent(this);
        getWhenClauses().add(when);
        return this;
    }

    /**
     * Creates an expression for the when node
     *
     * @return expression to be used as builder to configure the when node
     */
    public ExpressionClause<ChoiceDefinition> when() {
        WhenDefinition when = new WhenDefinition();
        when.setParent(this);
        getWhenClauses().add(when);
        ExpressionClause<ChoiceDefinition> clause = new ExpressionClause<ChoiceDefinition>(this);
        when.setExpression(clause);
        return clause;
    }

    /**
     * Sets the otherwise node
     * 
     * @return the builder
     */
    public ChoiceDefinition otherwise() {
        OtherwiseDefinition answer = new OtherwiseDefinition();
        answer.setParent(this);
        setOtherwise(answer);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public String getLabel() {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        List<WhenDefinition> list = getWhenClauses();
        for (WhenDefinition whenType : list) {
            buffer.append(whenType.getLabel());
        }
        return buffer.toString();
    }

    public List<WhenDefinition> getWhenClauses() {
        return whenClauses;
    }

    public void setWhenClauses(List<WhenDefinition> whenClauses) {
        this.whenClauses = whenClauses;
    }

    @SuppressWarnings("unchecked")
    public List<ProcessorDefinition> getOutputs() {
        if (otherwise != null) {
            return otherwise.getOutputs();
        } else if (whenClauses.isEmpty()) {
            return Collections.EMPTY_LIST;
        } else {
            WhenDefinition when = whenClauses.get(whenClauses.size() - 1);
            return when.getOutputs();
        }
    }

    public boolean isOutputSupported() {
        return true;
    }

    public OtherwiseDefinition getOtherwise() {
        return otherwise;
    }

    public void setOtherwise(OtherwiseDefinition otherwise) {
        this.otherwise = otherwise;
    }

    @Override
    protected void configureChild(ProcessorDefinition output) {
        if (whenClauses == null || whenClauses.isEmpty()) {
            return;
        }
        for (WhenDefinition when : whenClauses) {
            if (when.getExpression() instanceof ExpressionClause) {
                ExpressionClause clause = (ExpressionClause) when.getExpression();
                if (clause.getExpressionType() != null) {
                    // if using the Java DSL then the expression may have been set using the
                    // ExpressionClause which is a fancy builder to define expressions and predicates
                    // using fluent builders in the DSL. However we need afterwards a callback to
                    // reset the expression to the expression type the ExpressionClause did build for us
                    when.setExpression(clause.getExpressionType());
                }
            }
        }
    }
}
