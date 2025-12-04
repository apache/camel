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

    public static class LanguageOptionModel extends BaseOptionModel {}

    public LanguageModel() {}

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
    }
}
