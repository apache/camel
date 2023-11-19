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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Allows to set multiple headers on the message st the same time.
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "setHeaders")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetHeadersDefinition extends ProcessorDefinition<SetHeadersDefinition> {

    /** This is provided to support XML and YAML DSL */
    @XmlElementRef(name = "headers")
    private List<SetHeaderDefinition> setHeaderDefinitions = new java.util.ArrayList<>();

    public SetHeadersDefinition() {
    }

    public SetHeadersDefinition(Map<String, Expression> setHeaderDefs) {
        for (Entry<String, Expression> entry : setHeaderDefs.entrySet()) {
            setHeaderDefinitions.add(new SetHeaderDefinition(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Allow setting multiple headers using a single expression.
     *
     * @param headerNamesAndExprs
     */
    public SetHeadersDefinition(String header, Expression expr, Object... headerNamesAndExprs) {
        createSetHeaderDefinitions(header, expr, headerNamesAndExprs);
    }

    private void createSetHeaderDefinitions(String header, Expression expr, Object[] headerNamesAndExprs) {
        if (headerNamesAndExprs.length > 0 && headerNamesAndExprs.length % 2 != 0) {
            throw new IllegalArgumentException("Must have an even number of arguments!");
        }
        setHeaderDefinitions.add(new SetHeaderDefinition((String) header, (Expression) expr));
        for (int i = 0; i < headerNamesAndExprs.length; i += 2) {
            Object key = headerNamesAndExprs[i];
            Object value = headerNamesAndExprs[i + 1];
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("Keys must be Strings");
            }
            if (value instanceof String) {
                value = ExpressionBuilder.constantExpression(value);
            } else if (!(value instanceof Expression)) {
                throw new IllegalArgumentException("Values must be Expressions or Strings");
            }
            setHeaderDefinitions.add(new SetHeaderDefinition((String) key, (Expression) value));
        }

    }

    public void setSetHeaderDefinitions(List<SetHeaderDefinition> setHeaderDefs) {
        this.setHeaderDefinitions = setHeaderDefs;
    }

    public List<SetHeaderDefinition> getSetHeaderDefinitions() {
        return this.setHeaderDefinitions;
    }

    @Override
    public String getLabel() {
        return "setHeaders[" + getHeaderNames() + "]";
    }

    private String getHeaderNames() {
        StringBuilder sb = new StringBuilder();
        for (SetHeaderDefinition defs : setHeaderDefinitions) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(defs.getName());
        }
        return sb.toString();
    }

    @Override
    public String getShortName() {
        return "setHeaders";
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return Collections.emptyList();
    }

}
