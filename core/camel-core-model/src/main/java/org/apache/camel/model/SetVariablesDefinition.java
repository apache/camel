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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Allows setting multiple variables at the same time.
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "setVariables")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetVariablesDefinition extends ProcessorDefinition<SetVariablesDefinition> {

    /** This is provided to support XML and YAML DSL */
    @XmlElementRef(name = "variables")
    private List<SetVariableDefinition> variables = new java.util.ArrayList<>();

    public SetVariablesDefinition() {
    }

    protected SetVariablesDefinition(SetVariablesDefinition source) {
        super(source);
        this.variables = ProcessorDefinitionHelper.deepCopyDefinitions(source.variables);
    }

    @Override
    public SetVariablesDefinition copyDefinition() {
        return new SetVariablesDefinition(this);
    }

    /**
     * Allow setting multiple variables using a single expression.
     */
    public SetVariablesDefinition(Object... variableNamesAndExprs) {
        createSetVariableDefinitions(variableNamesAndExprs);
    }

    private void createSetVariableDefinitions(Object[] variableNamesAndExprs) {
        if (variableNamesAndExprs.length == 1 && variableNamesAndExprs[0] instanceof Map) {
            createVariablesFromMap((Map<?, ?>) variableNamesAndExprs[0]);
        } else if (variableNamesAndExprs.length % 2 != 0) {
            throw new IllegalArgumentException("Must be a Map or have an even number of arguments!");
        } else {
            for (int i = 0; i < variableNamesAndExprs.length; i += 2) {
                addVariable(variableNamesAndExprs[i], variableNamesAndExprs[i + 1]);
            }
        }
    }

    private void addVariable(Object key, Object value) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Keys must be Strings");
        }
        if (!(value instanceof Expression)) {
            // Assume it's a constant of some kind
            value = ExpressionBuilder.constantExpression(value);
        }
        variables.add(new SetVariableDefinition((String) key, (Expression) value));
    }

    private void createVariablesFromMap(Map<?, ?> variableMap) {
        for (Entry<?, ?> entry : variableMap.entrySet()) {
            addVariable(entry.getKey(), entry.getValue());
        }
    }

    public List<SetVariableDefinition> getVariables() {
        return variables;
    }

    public void setVariables(List<SetVariableDefinition> variables) {
        this.variables = variables;
    }

    @Override
    public String getLabel() {
        return "setVariables[" + getVariableNames() + "]";
    }

    private String getVariableNames() {
        StringJoiner sb = new StringJoiner(",");
        for (SetVariableDefinition def : variables) {
            sb.add(def.getName());
        }
        return sb.toString();
    }

    @Override
    public String getShortName() {
        return "setVariables";
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return Collections.emptyList();
    }

}
