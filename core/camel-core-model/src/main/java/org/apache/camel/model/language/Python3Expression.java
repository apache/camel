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
 * Evaluates a Python 3 expression.
 */
@Metadata(firstVersion = "4.23.0", label = "language,python", title = "Python 3",
          description = "Evaluates a Python 3 expression")
@XmlRootElement(name = "python3")
@XmlAccessorType(XmlAccessType.FIELD)
public class Python3Expression extends TypedExpressionDefinition {

    public Python3Expression() {
    }

    protected Python3Expression(Python3Expression source) {
        super(source);
    }

    public Python3Expression(String expression) {
        super(expression);
    }

    private Python3Expression(Builder builder) {
        super(builder);
    }

    @Override
    public Python3Expression copyDefinition() {
        return new Python3Expression(this);
    }

    @Override
    public String getLanguage() {
        return "python3";
    }

    /**
     * {@code Builder} is a specific builder for {@link Python3Expression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, Python3Expression> {

        @Override
        public Python3Expression end() {
            return new Python3Expression(this);
        }
    }
}
