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
package org.apache.camel.model.language;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Evaluates a Jactl script.
 */
@Metadata(firstVersion = "4.22.0", label = "language,script", title = "Jactl", description = "Evaluates a Jactl script")
@XmlRootElement(name = "jactl")
@XmlAccessorType(XmlAccessType.FIELD)
public class JactlExpression extends TypedExpressionDefinition {

    public JactlExpression() {
    }

    protected JactlExpression(JactlExpression source) {
        super(source);
    }

    public JactlExpression(String expression) {
        super(expression);
    }

    private JactlExpression(Builder builder) {
        super(builder);
    }

    @Override
    public JactlExpression copyDefinition() {
        return new JactlExpression(this);
    }

    @Override
    public String getLanguage() {
        return "jactl";
    }

    /**
     * {@code Builder} is a specific builder for {@link JactlExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, JactlExpression> {
        @Override
        public JactlExpression end() {
            return new JactlExpression(this);
        }
    }
}
