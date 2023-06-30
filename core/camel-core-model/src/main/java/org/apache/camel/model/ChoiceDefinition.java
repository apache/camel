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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.ExpressionFactory;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * Route messages based on a series of predicates
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "choice")
@XmlType(propOrder = { "whenClauses", "otherwise" })
@XmlAccessorType(XmlAccessType.FIELD)
public class ChoiceDefinition extends ProcessorDefinition<ChoiceDefinition> implements OutputNode {

    private transient boolean onlyWhenOrOtherwise = true;

    @XmlElementRef(name = "when")
    @AsPredicate
    @Metadata(description = "Sets the when nodes")
    private List<WhenDefinition> whenClauses = new ArrayList<>();
    @XmlElement
    @Metadata(description = "Sets the otherwise node")
    private OtherwiseDefinition otherwise;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String precondition;

    public ChoiceDefinition() {
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        // wrap the outputs into a list where we can on the inside control the
        // when/otherwise
        // but make it appear as a list on the outside
        return new AbstractList<>() {

            public ProcessorDefinition<?> get(int index) {
                if (index < whenClauses.size()) {
                    return whenClauses.get(index);
                }
                if (index == whenClauses.size()) {
                    return otherwise;
                }
                throw new IndexOutOfBoundsException("Index " + index + " is out of bounds with size " + size());
            }

            @Override
            public boolean add(ProcessorDefinition<?> def) {
                if (def instanceof WhenDefinition) {
                    return whenClauses.add((WhenDefinition) def);
                } else if (def instanceof OtherwiseDefinition) {
                    otherwise = (OtherwiseDefinition) def;
                    return true;
                }
                throw new IllegalArgumentException(
                        "Expected either a WhenDefinition or OtherwiseDefinition but was "
                                                   + ObjectHelper.classCanonicalName(def));
            }

            public int size() {
                return whenClauses.size() + (otherwise == null ? 0 : 1);
            }

            @Override
            public void clear() {
                whenClauses.clear();
                otherwise = null;
            }

            @Override
            public ProcessorDefinition<?> set(int index, ProcessorDefinition<?> element) {
                if (index < whenClauses.size()) {
                    if (element instanceof WhenDefinition) {
                        return whenClauses.set(index, (WhenDefinition) element);
                    }
                    throw new IllegalArgumentException(
                            "Expected WhenDefinition but was " + ObjectHelper.classCanonicalName(element));
                } else if (index == whenClauses.size()) {
                    ProcessorDefinition<?> old = otherwise;
                    otherwise = (OtherwiseDefinition) element;
                    return old;
                }
                throw new IndexOutOfBoundsException("Index " + index + " is out of bounds with size " + size());
            }

            @Override
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
    public String toString() {
        return "Choice[" + getWhenClauses() + (getOtherwise() != null ? " " + getOtherwise() : "") + "]";
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        if (onlyWhenOrOtherwise) {
            if (output instanceof WhenDefinition || output instanceof OtherwiseDefinition) {
                // okay we are adding a when or otherwise so allow any kind of
                // output after this again
                onlyWhenOrOtherwise = false;
            } else {
                throw new IllegalArgumentException(
                        "A new choice clause should start with a when() or otherwise(). "
                                                   + "If you intend to end the entire choice and are using endChoice() then use end() instead.");
            }
        }
        super.addOutput(output);
    }

    public String getPrecondition() {
        return precondition;
    }

    /**
     * Indicates whether this Choice EIP is in precondition mode or not. If so its branches (when/otherwise) are
     * evaluated during startup to keep at runtime only the branch that matched.
     */
    public void setPrecondition(String precondition) {
        this.precondition = precondition;
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
     * Indicates that this Choice EIP is in precondition mode, its branches (when/otherwise) are then evaluated during
     * startup to keep at runtime only the branch that matched.
     *
     * @return the builder
     */
    public ChoiceDefinition precondition() {
        return precondition(true);
    }

    /**
     * Indicates whether this Choice EIP is in precondition mode or not. If so its branches (when/otherwise) are
     * evaluated during startup to keep at runtime only the branch that matched.
     *
     * @param  precondition the flag indicating if it is in precondition mode or not.
     * @return              the builder
     */
    public ChoiceDefinition precondition(boolean precondition) {
        setPrecondition(Boolean.toString(precondition));
        return this;
    }

    /**
     * Sets the predicate for the when node
     *
     * @param  predicate the predicate
     * @return           the builder
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
        ExpressionClause<ChoiceDefinition> clause = new ExpressionClause<>(this);
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
    public void setId(String id) {
        // when setting id, we should set it on the fine grained element, if
        // possible
        if (otherwise != null) {
            otherwise.setId(id);
        } else if (!getWhenClauses().isEmpty()) {
            int size = getWhenClauses().size();
            getWhenClauses().get(size - 1).setId(id);
        } else {
            super.setId(id);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public String getShortName() {
        return "choice";
    }

    @Override
    public String getLabel() {
        return getOutputs().stream().map(ProcessorDefinition::getLabel)
                .collect(Collectors.joining(",", getShortName() + "[", "]"));
    }

    public List<WhenDefinition> getWhenClauses() {
        return whenClauses;
    }

    /**
     * Sets the when nodes
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
            ExpressionDefinition exp = when.getExpression();
            if (exp.getExpressionType() != null) {
                exp = exp.getExpressionType();
            }
            Predicate pre = exp.getPredicate();
            if (pre instanceof ExpressionClause) {
                ExpressionClause<?> clause = (ExpressionClause<?>) pre;
                if (clause.getExpressionType() != null) {
                    // if using the Java DSL then the expression may have been set using the
                    // ExpressionClause which is a fancy builder to define expressions and predicates
                    // using fluent builders in the DSL. However, we need afterwards a callback to
                    // reset the expression to the expression type the ExpressionClause did build for us
                    ExpressionFactory model = clause.getExpressionType();
                    if (model instanceof ExpressionDefinition) {
                        when.setExpression((ExpressionDefinition) model);
                    }
                }
            }
        }
    }
}
