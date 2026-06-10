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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;

public class LanguageModel extends ArtifactModel<LanguageModel.LanguageOptionModel> {

    protected String modelName;
    protected String modelJavaType;
    protected final List<LanguageFunctionModel> functions = new ArrayList<>();
    protected final List<LanguageOperatorModel> operators = new ArrayList<>();

    public static class LanguageOptionModel extends BaseOptionModel {

    }

    public LanguageModel() {
    }

    @Override
    public Kind getKind() {
        return Kind.language;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelJavaType() {
        return modelJavaType;
    }

    public void setModelJavaType(String modelJavaType) {
        this.modelJavaType = modelJavaType;
    }

    public List<LanguageFunctionModel> getFunctions() {
        return functions;
    }

    public void addFunction(LanguageFunctionModel function) {
        functions.add(function);
    }

    public List<LanguageOperatorModel> getOperators() {
        return operators;
    }

    public void addOperator(LanguageOperatorModel operator) {
        operators.add(operator);
    }

    public static class LanguageFunctionModel extends BaseOptionModel {

        /**
         * The name of the constant that defines the function.
         */
        private String constantName;

        /**
         * Whether this function allows to do OGNL method calls.
         */
        boolean ognl;

        /**
         * Optional prefix for the function
         */
        private String prefix;

        /**
         * Optional suffix for the function
         */
        private String suffix;

        /**
         * Structured parameter metadata for the function arguments.
         */
        private final List<FunctionParamModel> params = new ArrayList<>();

        /**
         * Usage examples for documentation and AI tooling.
         */
        private final List<String> examples = new ArrayList<>();

        public String getConstantName() {
            return constantName;
        }

        public void setConstantName(String constantName) {
            this.constantName = constantName;
        }

        public boolean isOgnl() {
            return ognl;
        }

        public void setOgnl(boolean ognl) {
            this.ognl = ognl;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        @Override
        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }

        public List<FunctionParamModel> getParams() {
            return params;
        }

        public void addParam(FunctionParamModel param) {
            params.add(param);
        }

        public List<String> getExamples() {
            return examples;
        }

        public void addExample(String example) {
            examples.add(example);
        }
    }

    public static class LanguageOperatorModel extends BaseOptionModel {

        private String constantName;
        private String operatorKind;
        private String operatorSyntax;
        private int precedence;
        private final List<String> examples = new ArrayList<>();

        public String getConstantName() {
            return constantName;
        }

        public void setConstantName(String constantName) {
            this.constantName = constantName;
        }

        public String getOperatorKind() {
            return operatorKind;
        }

        public void setOperatorKind(String operatorKind) {
            this.operatorKind = operatorKind;
        }

        public String getOperatorSyntax() {
            return operatorSyntax;
        }

        public void setOperatorSyntax(String operatorSyntax) {
            this.operatorSyntax = operatorSyntax;
        }

        public int getPrecedence() {
            return precedence;
        }

        public void setPrecedence(int precedence) {
            this.precedence = precedence;
        }

        public List<String> getExamples() {
            return examples;
        }

        public void addExample(String example) {
            examples.add(example);
        }
    }

    public static class FunctionParamModel {
        private String name;
        private String javaType;
        private boolean required;
        private String defaultValue;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

}
