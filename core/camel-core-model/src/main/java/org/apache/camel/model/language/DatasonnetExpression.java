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
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * To use DataSonnet scripts for message transformations.
 */
@Metadata(firstVersion = "3.7.0", label = "language,transformation", title = "DataSonnet")
@XmlRootElement(name = "datasonnet")
@XmlAccessorType(XmlAccessType.FIELD)
public class DatasonnetExpression extends SingleInputTypedExpressionDefinition {

    @XmlAttribute(name = "bodyMediaType")
    private String bodyMediaType;
    @XmlAttribute(name = "outputMediaType")
    private String outputMediaType;

    public DatasonnetExpression() {
    }

    public DatasonnetExpression(String expression) {
        super(expression);
    }

    public DatasonnetExpression(Expression expression) {
        super(expression);
    }

    private DatasonnetExpression(Builder builder) {
        super(builder);
        this.bodyMediaType = builder.bodyMediaType;
        this.outputMediaType = builder.outputMediaType;
    }

    @Override
    public String getLanguage() {
        return "datasonnet";
    }

    public String getBodyMediaType() {
        return bodyMediaType;
    }

    /**
     * The String representation of the message's body MediaType
     */
    public void setBodyMediaType(String bodyMediaType) {
        this.bodyMediaType = bodyMediaType;
    }

    public String getOutputMediaType() {
        return outputMediaType;
    }

    /**
     * The String representation of the MediaType to output
     */
    public void setOutputMediaType(String outputMediaType) {
        this.outputMediaType = outputMediaType;
    }

    /**
     * {@code Builder} is a specific builder for {@link DatasonnetExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, DatasonnetExpression> {

        private String bodyMediaType;
        private String outputMediaType;

        /**
         * The String representation of the message's body MediaType
         */
        public Builder bodyMediaType(String bodyMediaType) {
            this.bodyMediaType = bodyMediaType;
            return this;
        }

        /**
         * The String representation of the MediaType to output
         */
        public Builder outputMediaType(String outputMediaType) {
            this.outputMediaType = outputMediaType;
            return this;
        }

        @Override
        public DatasonnetExpression end() {
            return new DatasonnetExpression(this);
        }
    }
}
