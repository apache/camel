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
 * Evaluates a Spring expression (SpEL).
 */
@Metadata(firstVersion = "2.7.0", label = "language,spring", title = "SpEL")
@XmlRootElement(name = "spel")
@XmlAccessorType(XmlAccessType.FIELD)
public class SpELExpression extends TypedExpressionDefinition {

    public SpELExpression() {
    }

    public SpELExpression(String expression) {
        super(expression);
    }

    private SpELExpression(Builder builder) {
        super(builder);
    }

    @Override
    public String getLanguage() {
        return "spel";
    }

    /**
     * {@code Builder} is a specific builder for {@link SpELExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, SpELExpression> {

        @Override
        public SpELExpression end() {
            return new SpELExpression(this);
        }
    }
}
