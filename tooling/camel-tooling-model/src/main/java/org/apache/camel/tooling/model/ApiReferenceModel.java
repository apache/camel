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

/**
 * A compact API reference of a core Camel class a route author's Java or Groovy code touches (Exchange, Message,
 * CamelContext, ...): the methods that matter, one line each, with the one-line usage and the common mistakes.
 * Generated from the {@code @Metadata(label = "api")} annotations on the real methods so it cannot drift from the code,
 * and meant for tooling and AI assistants that need the API before writing code, not for the javadoc.
 */
public class ApiReferenceModel extends ArtifactModel<ApiReferenceModel.ApiMethodOptionModel> {

    protected final List<ApiMethodOptionModel> options = new ArrayList<>();

    public ApiReferenceModel() {
    }

    public void addOption(ApiMethodOptionModel option) {
        options.add(option);
    }

    /** The documented methods, one entry per method name with every overload as a signature. */
    @Override
    public List<ApiMethodOptionModel> getOptions() {
        return options;
    }

    @Override
    public Kind getKind() {
        return Kind.api;
    }

    /**
     * A documented method: the name, the signatures of its overloads, the return type as javaType, the description and
     * the usage examples.
     */
    public static class ApiMethodOptionModel extends BaseOptionModel {

        private final List<String> signatures = new ArrayList<>();
        private final List<String> examples = new ArrayList<>();

        /**
         * The signatures of the overloads, simple type names, e.g. {@code <T> T getHeader(String name, Class<T> type)}.
         */
        public List<String> getSignatures() {
            return signatures;
        }

        public void addSignature(String signature) {
            signatures.add(signature);
        }

        /** Usage examples, e.g. {@code exchange.getMessage().getBody(String.class)}. */
        public List<String> getExamples() {
            return examples;
        }

        public void addExample(String example) {
            examples.add(example);
        }
    }
}
