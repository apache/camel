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

import org.apache.camel.spi.Metadata;

/**
 * Call a wasm (web assembly) function.
 */
@Metadata(firstVersion = "4.5.0", label = "language", title = "Wasm")
@XmlRootElement(name = "wasm")
@XmlAccessorType(XmlAccessType.FIELD)
public class WasmExpression extends TypedExpressionDefinition {

    @XmlAttribute
    @Metadata(required = true)
    private String module;

    public WasmExpression() {
    }

    public WasmExpression(String expression) {
        super(expression);
    }

    public WasmExpression(String expression, String module) {
        super(expression);
        this.module = module;
    }

    private WasmExpression(Builder builder) {
        super(builder);
        this.module = builder.module;
    }

    public String getModule() {
        return module;
    }

    /**
     * Set the module (the distributable, loadable, and executable unit of code in WebAssembly) resource that provides
     * the expression function.
     */
    public void setModule(String module) {
        this.module = module;
    }

    @Override
    public String getLanguage() {
        return "wasm";
    }

    /**
     * {@code Builder} is a specific builder for {@link WasmExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, WasmExpression> {

        private String module;

        /**
         * Set the module, the distributable, loadable, and executable unit of code in WebAssembly that provides the
         * expression function.
         */
        public Builder module(String module) {
            this.module = module;
            return this;
        }

        @Override
        public WasmExpression end() {
            return new WasmExpression(this);
        }
    }
}
