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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Gets a property from the Exchange.
 */
@Metadata(firstVersion = "2.0.0", label = "language,core", title = "ExchangeProperty")
@XmlRootElement(name = "exchangeProperty")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExchangePropertyExpression extends ExpressionDefinition {

    public ExchangePropertyExpression() {
    }

    public ExchangePropertyExpression(String name) {
        super(name);
    }

    private ExchangePropertyExpression(Builder builder) {
        super(builder);
    }

    @Override
    public String getLanguage() {
        return "exchangeProperty";
    }

    /**
     * {@code Builder} is a specific builder for {@link ExchangePropertyExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, ExchangePropertyExpression> {

        @Override
        public ExchangePropertyExpression end() {
            return new ExchangePropertyExpression(this);
        }
    }
}
