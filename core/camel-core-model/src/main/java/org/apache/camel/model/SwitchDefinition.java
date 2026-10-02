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
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;

/** Routes a message to one fixed endpoint using a literal lookup table. */
@Metadata(firstVersion = "4.23.0", label = "eip,routing",
          aliases = { "decision-table", "dispatch-table", "lookup-table" },
          description = "Evaluates a selector once and dispatches to a fixed endpoint by literal scalar values")
@XmlRootElement(name = "switch")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = { "selector", "cases", "otherwise" })
public class SwitchDefinition extends NoOutputDefinition<SwitchDefinition> {
    @XmlElement(required = true)
    @Metadata(required = true,
              description = "Expression evaluated once per entry. Returns a scalar value to match against the literal cases.")
    private ExpressionSubElementDefinition selector;
    @XmlElement(name = "case")
    @Metadata(description = "Literal cases. Duplicate values are rejected at startup, ignoring case.")
    private List<SwitchCaseDefinition> cases = new ArrayList<>();
    @XmlElement
    @Metadata(description = "Fixed fallback URI for null or unmatched selector results. Without a fallback processing continues.")
    private SwitchOtherwiseDefinition otherwise;

    public SwitchDefinition() {
    }

    public SwitchDefinition(Expression selector) {
        this.selector = new ExpressionSubElementDefinition(ExpressionNodeHelper.toExpressionDefinition(selector));
    }

    protected SwitchDefinition(SwitchDefinition source) {
        super(source);
        this.selector = source.selector != null ? source.selector.copyDefinition() : null;
        for (SwitchCaseDefinition c : source.cases) {
            SwitchCaseDefinition copy = c.copyDefinition();
            copy.setParent(this);
            cases.add(copy);
        }
        // copied after the cases, so the otherwise node keeps coming last in the processor index
        setOtherwise(source.otherwise != null ? source.otherwise.copyDefinition() : null);
    }

    @Override
    public SwitchDefinition copyDefinition() {
        return new SwitchDefinition(this);
    }

    public ExpressionSubElementDefinition getSelector() {
        return selector;
    }

    @Override
    public void preCreateProcessor() {
        if (selector != null && selector.getExpressionType() != null
                && selector.getExpressionType().getExpressionValue() instanceof ExpressionClause<?> clause
                && clause.getExpressionType() instanceof ExpressionDefinition expression) {
            selector.setExpressionType(expression);
        }
    }

    public void setSelector(ExpressionSubElementDefinition selector) {
        this.selector = selector;
    }

    public List<SwitchCaseDefinition> getCases() {
        return cases;
    }

    public void setCases(List<SwitchCaseDefinition> cases) {
        this.cases = cases;
    }

    public SwitchOtherwiseDefinition getOtherwise() {
        return otherwise;
    }

    public void setOtherwise(SwitchOtherwiseDefinition otherwise) {
        this.otherwise = otherwise;
        prepareOtherwiseDefinition();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        prepareOtherwiseDefinition();
    }

    @Override
    public void setGeneratedId(String id) {
        super.setGeneratedId(id);
        prepareOtherwiseDefinition();
    }

    /** The fallback send node, used by route traversal and processor creation. */
    @XmlTransient
    public ToDefinition getOtherwiseDefinition() {
        return otherwise != null ? otherwise.getToDefinition() : null;
    }

    /** Prepare the fallback node before processor creation and management instrumentation. */
    public void prepareOtherwiseDefinition() {
        ToDefinition otherwiseDefinition = getOtherwiseDefinition();
        if (otherwiseDefinition == null) {
            return;
        }
        otherwiseDefinition.setParent(this);
        if (otherwise.getEndpointProducerBuilder() != null) {
            otherwiseDefinition.setEndpointProducerBuilder(otherwise.getEndpointProducerBuilder());
        } else {
            otherwiseDefinition.setUri(otherwise.getUri());
        }
        if (getId() != null) {
            if (hasCustomIdAssigned()) {
                otherwiseDefinition.setId(getId() + "-otherwise");
            } else {
                otherwiseDefinition.setGeneratedId(getId() + "-otherwise");
            }
        }
    }

    /** Add a scalar literal and its destination. */
    public SwitchDefinition doCase(String value, String uri) {
        return doCase(new SwitchCaseDefinition(value, uri));
    }

    /** Add a scalar literal and its destination using the Endpoint DSL. */
    public SwitchDefinition doCase(String value, @AsEndpointUri EndpointProducerBuilder endpoint) {
        SwitchCaseDefinition c = new SwitchCaseDefinition();
        c.setValue(value);
        c.setEndpointProducerBuilder(endpoint);
        return doCase(c);
    }

    /** Add a case definition. */
    public SwitchDefinition doCase(SwitchCaseDefinition value) {
        value.setParent(this);
        cases.add(value);
        if (getCamelContext() != null && (getCamelContext().isSourceLocationEnabled()
                || getCamelContext().isDebugging() || getCamelContext().isDebugStandby()
                || getCamelContext().isTracing() || getCamelContext().isTracingStandby())) {
            ProcessorDefinitionHelper.prepareSourceLocation(ProcessorDefinitionHelper.getResource(this), value);
        }
        return this;
    }

    /** Build a scalar case. */
    public CaseBuilder doCase(String value) {
        return new CaseBuilder(this, new SwitchCaseDefinition(value, null));
    }

    /** Set the fixed fallback destination. */
    public SwitchDefinition otherwise(String uri) {
        SwitchOtherwiseDefinition fallback = null;
        if (uri != null) {
            fallback = new SwitchOtherwiseDefinition();
            fallback.setUri(uri);
        }
        setOtherwise(fallback);
        return this;
    }

    /** Set the fixed fallback destination using the Endpoint DSL. */
    public SwitchDefinition otherwise(@AsEndpointUri EndpointProducerBuilder endpoint) {
        SwitchOtherwiseDefinition fallback = null;
        if (endpoint != null) {
            fallback = new SwitchOtherwiseDefinition();
            fallback.setEndpointProducerBuilder(endpoint);
        }
        setOtherwise(fallback);
        return this;
    }

    @Override
    public List<NamedNode> getChildren() {
        List<NamedNode> children = new ArrayList<>(cases);
        ToDefinition fallback = getOtherwiseDefinition();
        if (fallback != null) {
            children.add(fallback);
        }
        return children;
    }

    @Override
    public String getShortName() {
        return "switch";
    }

    @Override
    public String getLabel() {
        return "switch[" + selector + "]";
    }

    @Override
    public String toString() {
        return getLabel() + cases;
    }

    /** A case builder that permits exactly one endpoint and no nested route steps. */
    public static final class CaseBuilder {
        private final SwitchDefinition parent;
        private final SwitchCaseDefinition definition;

        private CaseBuilder(SwitchDefinition parent, SwitchCaseDefinition definition) {
            this.parent = parent;
            this.definition = definition;
        }

        public CaseBuilder id(String id) {
            definition.setId(id);
            return this;
        }

        public CaseBuilder description(String description) {
            definition.setDescription(description);
            return this;
        }

        public CaseBuilder note(String note) {
            definition.setNote(note);
            return this;
        }

        public SwitchDefinition to(@AsEndpointUri EndpointProducerBuilder endpoint) {
            definition.setEndpointProducerBuilder(endpoint);
            return parent.doCase(definition);
        }

        public SwitchDefinition to(String uri) {
            definition.setUri(uri);
            return parent.doCase(definition);
        }
    }
}
