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
package org.apache.camel.component.ai.observability;

/**
 * Context for a single GenAI client operation observation.
 */
public final class GenAiObservationContext {

    private final GenAiOperationName operationName;
    private final String system;
    private final String requestModel;
    private final String componentScheme;

    private GenAiObservationContext(Builder builder) {
        this.operationName = builder.operationName;
        this.system = builder.system;
        this.requestModel = builder.requestModel;
        this.componentScheme = builder.componentScheme;
    }

    public GenAiOperationName operationName() {
        return operationName;
    }

    public String system() {
        return system;
    }

    public String requestModel() {
        return requestModel;
    }

    public String componentScheme() {
        return componentScheme;
    }

    public String spanName() {
        String model = requestModel != null && !requestModel.isBlank() ? requestModel : "unknown";
        return operationName.value() + " " + model;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GenAiOperationName operationName = GenAiOperationName.CHAT;
        private String system = "unknown";
        private String requestModel;
        private String componentScheme;

        public Builder operationName(GenAiOperationName operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder requestModel(String requestModel) {
            this.requestModel = requestModel;
            return this;
        }

        public Builder componentScheme(String componentScheme) {
            this.componentScheme = componentScheme;
            return this;
        }

        public GenAiObservationContext build() {
            return new GenAiObservationContext(this);
        }
    }
}
