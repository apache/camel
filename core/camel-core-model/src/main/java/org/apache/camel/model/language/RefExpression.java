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
 * Uses an existing expression from the registry.
 */
@Metadata(firstVersion = "2.8.0", label = "language,core", title = "Ref")
@XmlRootElement(name = "ref")
@XmlAccessorType(XmlAccessType.FIELD)
public class RefExpression extends TypedExpressionDefinition {

    public RefExpression() {
    }

    public RefExpression(String ref) {
        super(ref);
    }

    private RefExpression(Builder builder) {
        super(builder);
    }

    @Override
    public String getLanguage() {
        return "ref";
    }

    /**
     * {@code Builder} is a specific builder for {@link RefExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, RefExpression> {

        @Override
        public RefExpression end() {
            return new RefExpression(this);
        }
    }
}
