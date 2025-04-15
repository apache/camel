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

import java.util.ArrayList;
import java.util.List;

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

/**
 * Route messages based on a series of predicates
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "choice")
@XmlType(propOrder = { "whenClauses", "otherwise" })
@XmlAccessorType(XmlAccessType.FIELD)
public class ChoiceDefinition extends NoOutputDefinition<ChoiceDefinition> {

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

    protected ChoiceDefinition(ChoiceDefinition source) {
        super(source);
        this.whenClauses = ProcessorDefinitionHelper.deepCopyDefinitions(source.whenClauses);
        this.otherwise = source.otherwise != null ? source.otherwise.copyDefinition() : null;
        this.precondition = source.precondition;
    }

    @Override
    public ChoiceDefinition copyDefinition() {
        return new ChoiceDefinition(this);
    }

    @Override
    public String toString() {
        return "Choice[" + getWhenClauses() + (getOtherwise() != null ? " " + getOtherwise() : "") + "]";
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
    public void addOutput(ProcessorDefinition<?> output) {
        if (otherwise != null) {
            output.setParent(this);
            otherwise.addOutput(output);
        } else if (!whenClauses.isEmpty()) {
            output.setParent(this);
            WhenDefinition last = whenClauses.get(whenClauses.size() - 1);
            last.addOutput(output);
        } else {
            super.addOutput(output);
        }
    }

    public void addOutput(WhenDefinition when) {
        when.setParent(this);
        whenClauses.add(when);
    }

    public void addOutput(OtherwiseDefinition other) {
        other.setParent(this);
        this.otherwise = other;
    }

    /**
     * Whether to disable this EIP from the route during build time. Once an EIP has been disabled then it cannot be
     * enabled later at runtime.
     */
    @Override
    public ChoiceDefinition disabled(String disabled) {
        // special to disable when/otherwise
        if (otherwise != null && otherwise.getOutputs().isEmpty()) {
            otherwise.setDisabled(disabled);
        } else if (!whenClauses.isEmpty()) {
            WhenDefinition last = whenClauses.get(whenClauses.size() - 1);
            if (last.getOutputs().isEmpty()) {
                last.setDisabled(disabled);
            } else {
                super.disabled(disabled);
            }
        } else {
            super.disabled(disabled);
        }
        return this;
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
        addClause(new WhenDefinition((Predicate) clause));
        return clause;
    }

    /**
     * Sets the otherwise node
     *
     * @return the builder
     */
    public ChoiceDefinition otherwise() {
        if (this.otherwise != null) {
            throw new IllegalArgumentException("Cannot add a 2nd otherwise to this choice: " + this + ". If you have nested choice then you may need to end().endChoice() to go back to parent choice.");
        }
        OtherwiseDefinition answer = new OtherwiseDefinition();
        addClause(answer);
        return this;
    }

    // TODO: delete me
    public ChoiceDefinition otherwise(String id) {
        if (this.otherwise != null) {
            throw new IllegalArgumentException("Cannot add a 2nd otherwise to this choice: " + this + ". If you have nested choice then you may need to end().endChoice() to go back to parent choice.");
        }
        OtherwiseDefinition answer = new OtherwiseDefinition();
        answer.setId(id);
        addClause(answer);
        return this;
    }

    private void addClause(WhenDefinition when) {
        popBlock();
        addOutput(when);
        pushBlock(when);
    }

    private void addClause(OtherwiseDefinition other) {
        popBlock();
        addOutput(other);
        pushBlock(other);
    }

    @Override
    public void setId(String id) {
        // when setting id, we should set it on the fine grained element, if possible
        if (otherwise != null) {
            otherwise.setId(id);
        } else if (!getWhenClauses().isEmpty()) {
            var last = getWhenClauses().get(getWhenClauses().size() - 1);
            last.setId(id);
        } else {
            super.setId(id);
        }
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        var answer = new ArrayList<ProcessorDefinition<?>>();
        for (WhenDefinition when : whenClauses) {
            answer.addAll(when.getOutputs());
        }
        if (otherwise != null) {
            answer.addAll(otherwise.getOutputs());
        }
        return answer;
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public String getShortName() {
        return "choice";
    }

    @Override
    public String getLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append("choice[");
        for (WhenDefinition when : whenClauses) {
            sb.append(when.getLabel());
        }
        if (otherwise != null) {
            sb.append(otherwise.getLabel());
        }
        sb.append("]");
        return sb.toString();
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
    public void preCreateProcessor() {
        if (whenClauses == null || whenClauses.isEmpty()) {
            return;
        }
        for (WhenDefinition when : whenClauses) {
            ExpressionDefinition exp = when.getExpression();
            if (exp.getExpressionType() != null) {
                exp = exp.getExpressionType();
            }
            Predicate pre = exp.getPredicate();
            if (pre instanceof ExpressionClause clause) {
                if (clause.getExpressionType() != null) {
                    // if using the Java DSL then the expression may have been set using the
                    // ExpressionClause which is a fancy builder to define expressions and predicates
                    // using fluent builders in the DSL. However, we need afterwards a callback to
                    // reset the expression to the expression type the ExpressionClause did build for us
                    ExpressionFactory model = clause.getExpressionType();
                    if (model instanceof ExpressionDefinition expressionDefinition) {
                        when.setExpression(expressionDefinition);
                    }
                }
            }
        }
    }

}
