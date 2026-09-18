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
 * Evaluates a JavaScript expression using QuickJS4J.
 */
@Metadata(firstVersion = "4.23.0", label = "language,javascript", title = "QuickJS",
          description = "Evaluates a JavaScript expression using QuickJS4J")
@XmlRootElement(name = "quickjs")
@XmlAccessorType(XmlAccessType.FIELD)
public class QuickjsExpression extends TypedExpressionDefinition {

    public QuickjsExpression() {
    }

    protected QuickjsExpression(QuickjsExpression source) {
        super(source);
    }

    public QuickjsExpression(String expression) {
        super(expression);
    }

    private QuickjsExpression(Builder builder) {
        super(builder);
    }

    @Override
    public QuickjsExpression copyDefinition() {
        return new QuickjsExpression(this);
    }

    @Override
    public String getLanguage() {
        return "quickjs";
    }

    /**
     * {@code Builder} is a specific builder for {@link QuickjsExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, QuickjsExpression> {

        @Override
        public QuickjsExpression end() {
            return new QuickjsExpression(this);
        }
    }
}
