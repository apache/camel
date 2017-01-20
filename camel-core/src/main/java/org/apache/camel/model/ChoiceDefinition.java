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

import java.util.AbstractList;
import java.util.ArrayList;
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
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;

/**
 * Routes messages based on a series of predicates
 *
 * @version
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "choice")
@XmlAccessorType(XmlAccessType.FIELD)
public class ChoiceDefinition extends ProcessorDefinition<ChoiceDefinition> {
    @XmlElementRef @AsPredicate
    private List<WhenDefinition> whenClauses = new ArrayList<WhenDefinition>();
    @XmlElement
    private OtherwiseDefinition otherwise;

    private transient boolean onlyWhenOrOtherwise = true;

    public ChoiceDefinition() {
    }
    
    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        // wrap the outputs into a list where we can on the inside control the when/otherwise
        // but make it appear as a list on the outside
        return new AbstractList<ProcessorDefinition<?>>() {

            public ProcessorDefinition<?> get(int index) {
                if (index < whenClauses.size()) {
                    return whenClauses.get(index);
                } 
                if (index == whenClauses.size()) {
                    return otherwise;
                }
                throw new IndexOutOfBoundsException("Index " + index + " is out of bounds with size " + size());
            }

            public boolean add(ProcessorDefinition<?> def) {
                if (def instanceof WhenDefinition) {
                    return whenClauses.add((WhenDefinition)def);
                } else if (def instanceof OtherwiseDefinition) {
                    otherwise = (OtherwiseDefinition)def;
                    return true;
                }
                throw new IllegalArgumentException("Expected either a WhenDefinition or OtherwiseDefinition but was "
                        + ObjectHelper.classCanonicalName(def));
            }

            public int size() {
                return whenClauses.size() + (otherwise == null ? 0 : 1);
            }

            public void clear() {
                whenClauses.clear();
                otherwise = null;
            }

            public ProcessorDefinition<?> set(int index, ProcessorDefinition<?> element) {
                if (index < whenClauses.size()) {
                    if (element instanceof WhenDefinition) {
                        return whenClauses.set(index, (WhenDefinition)element);
                    }
                    throw new IllegalArgumentException("Expected WhenDefinition but was "
                            + ObjectHelper.classCanonicalName(element));
                } else if (index == whenClauses.size()) {
                    ProcessorDefinition<?> old = otherwise;
                    otherwise = (OtherwiseDefinition)element;
                    return old;
                }
                throw new IndexOutOfBoundsException("Index " + index + " is out of bounds with size " + size());
            }

            public ProcessorDefinition<?> remove(int index) {
                if (index < whenClauses.size()) {
                    return whenClauses.remove(index);
                } else if (index == whenClauses.size()) {
                    ProcessorDefinition<?> old = otherwise;
                    otherwise = null;
                    return old;
                }
                throw new IndexOutOfBoundsException("Index " + index + " is out of bounds with size " + size());
            }
        };
    }

    @Override
    public boolean isOutputSupported() {
        return true;
    }
    
    @Override
    public String toString() {
        return "Choice[" + getWhenClauses() + (getOtherwise() != null ? " " + getOtherwise() : "") + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        List<FilterProcessor> filters = new ArrayList<FilterProcessor>();
        for (WhenDefinition whenClause : whenClauses) {
            FilterProcessor filter = (FilterProcessor) createProcessor(routeContext, whenClause);
            filters.add(filter);
        }
        Processor otherwiseProcessor = null;
        if (otherwise != null) {
            otherwiseProcessor = createProcessor(routeContext, otherwise);
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        if (onlyWhenOrOtherwise) {
            if (output instanceof WhenDefinition || output instanceof OtherwiseDefinition) {
                // okay we are adding a when or otherwise so allow any kind of output after this again
                onlyWhenOrOtherwise = false;
            } else {
                throw new IllegalArgumentException("A new choice clause should start with a when() or otherwise(). "
                    + "If you intend to end the entire choice and are using endChoice() then use end() instead.");
            }
        }
        super.addOutput(output);
    }

    @Override
    public ProcessorDefinition<?> end() {
        // we end a block so only when or otherwise is supported
        onlyWhenOrOtherwise = true;
        return super.end();
    }

    @Override
    public ChoiceDefinition endChoice() {
        // we end a block so only when or otherwise is supported
        onlyWhenOrOtherwise = true;
        return super.endChoice();
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the predicate for the when node
     *
     * @param predicate the predicate
     * @return the builder
     */
    public ChoiceDefinition when(@AsPredicate Predicate predicate) {
        addClause(new WhenDefinition(predicate));
        return this;
    }

    /**
     * Creates an expression for the when node
     *
     * @return expression to be used as builder to configure the when node
     */
    @AsPredicate
    public ExpressionClause<ChoiceDefinition> when() {
        ExpressionClause<ChoiceDefinition> clause = new ExpressionClause<ChoiceDefinition>(this);
        addClause(new WhenDefinition(clause));
        return clause;
    }
    
    private void addClause(ProcessorDefinition<?> when) {
        onlyWhenOrOtherwise = true;
        popBlock();
        addOutput(when);
        pushBlock(when);
    }
    
    /**
     * Sets the otherwise node
     *
     * @return the builder
     */
    public ChoiceDefinition otherwise() {
        OtherwiseDefinition answer = new OtherwiseDefinition();
        addClause(answer);
        return this;
    }

    @Override
    public void setId(String value) {
        // when setting id, we should set it on the fine grained element, if possible
        if (otherwise != null) {
            otherwise.setId(value);
        } else if (!getWhenClauses().isEmpty()) {
            int size = getWhenClauses().size();
            getWhenClauses().get(size - 1).setId(value);
        } else {
            super.setId(value);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public String getLabel() {
        CollectionStringBuffer buffer = new CollectionStringBuffer("choice[");
        List<WhenDefinition> list = getWhenClauses();
        for (WhenDefinition whenType : list) {
            buffer.append(whenType.getLabel());
        }
        buffer.append("]");
        return buffer.toString();
    }

    public List<WhenDefinition> getWhenClauses() {
        return whenClauses;
    }

    /**
     * Sets the when clauses
     */
    public void setWhenClauses(List<WhenDefinition> whenClauses) {
        this.whenClauses = whenClauses;
    }

    public OtherwiseDefinition getOtherwise() {
        return otherwise;
    }

    public void setOtherwise(OtherwiseDefinition otherwise) {
        this.otherwise = otherwise;
    }

    @Override
    public void configureChild(ProcessorDefinition<?> output) {
        if (whenClauses == null || whenClauses.isEmpty()) {
            return;
        }
        for (WhenDefinition when : whenClauses) {
            if (when.getExpression() instanceof ExpressionClause) {
                ExpressionClause<?> clause = (ExpressionClause<?>) when.getExpression();
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
