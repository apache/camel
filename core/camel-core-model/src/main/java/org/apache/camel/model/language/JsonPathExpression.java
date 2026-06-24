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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Evaluates a JSONPath expression against a JSON message body.
 */
@Metadata(firstVersion = "2.13.0", label = "language,json", title = "JSONPath",
          description = "Evaluates a JSONPath expression against a JSON message body")
@XmlRootElement(name = "jsonpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonPathExpression extends SingleInputTypedExpressionDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to suppress exceptions such as PathNotFoundException.")
    private String suppressExceptions;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean",
              description = "Whether to allow in inlined Simple exceptions in the JSONPath expression.")
    private String allowSimple;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean",
              description = "Whether to allow using the easy predicate parser to pre-parse predicates.")
    private String allowEasyPredicate;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to write the output of each row/element as a JSON String value instead of a Map/POJO value.")
    private String writeAsString;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to unpack a single element json-array into an object.")
    private String unpackArray;
    @XmlAttribute
    @Metadata(label = "advanced",
              enums = "DEFAULT_PATH_LEAF_TO_NULL,ALWAYS_RETURN_LIST,AS_PATH_LIST,SUPPRESS_EXCEPTIONS,REQUIRE_PROPERTIES",
              description = "To configure additional options on JSONPath. Multiple values can be separated by comma.")
    private String option;

    public JsonPathExpression() {
    }

    private JsonPathExpression(JsonPathExpression source) {
        super(source);
        this.suppressExceptions = source.suppressExceptions;
        this.allowSimple = source.allowSimple;
        this.allowEasyPredicate = source.allowEasyPredicate;
        this.writeAsString = source.writeAsString;
        this.unpackArray = source.unpackArray;
        this.option = source.option;
    }

    public JsonPathExpression(String expression) {
        super(expression);
    }

    private JsonPathExpression(Builder builder) {
        super(builder);
        this.suppressExceptions = builder.suppressExceptions;
        this.allowSimple = builder.allowSimple;
        this.allowEasyPredicate = builder.allowEasyPredicate;
        this.writeAsString = builder.writeAsString;
        this.unpackArray = builder.unpackArray;
        this.option = builder.option;
    }

    @Override
    public JsonPathExpression copyDefinition() {
        return new JsonPathExpression(this);
    }

    public String getSuppressExceptions() {
        return suppressExceptions;
    }

    public String getAllowSimple() {
        return allowSimple;
    }

    public void setAllowSimple(String allowSimple) {
        this.allowSimple = allowSimple;
    }

    public String getAllowEasyPredicate() {
        return allowEasyPredicate;
    }

    public void setAllowEasyPredicate(String allowEasyPredicate) {
        this.allowEasyPredicate = allowEasyPredicate;
    }

    public void setSuppressExceptions(String suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    public String getWriteAsString() {
        return writeAsString;
    }

    public void setWriteAsString(String writeAsString) {
        this.writeAsString = writeAsString;
    }

    public String getUnpackArray() {
        return unpackArray;
    }

    public void setUnpackArray(String unpackArray) {
        this.unpackArray = unpackArray;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    @Override
    public String getLanguage() {
        return "jsonpath";
    }

    /**
     * {@code Builder} is a specific builder for {@link JsonPathExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, JsonPathExpression> {

        private String suppressExceptions;
        private String allowSimple;
        private String allowEasyPredicate;
        private String writeAsString;
        private String unpackArray;
        private String option;

        /**
         * Whether to suppress exceptions such as PathNotFoundException.
         */
        public Builder suppressExceptions(String suppressExceptions) {
            this.suppressExceptions = suppressExceptions;
            return this;
        }

        /**
         * Whether to suppress exceptions such as PathNotFoundException.
         */
        public Builder suppressExceptions(boolean suppressExceptions) {
            this.suppressExceptions = Boolean.toString(suppressExceptions);
            return this;
        }

        /**
         * Whether to allow in inlined Simple exceptions in the JSONPath expression
         */
        public Builder allowSimple(String allowSimple) {
            this.allowSimple = allowSimple;
            return this;
        }

        /**
         * Whether to allow in inlined Simple exceptions in the JSONPath expression
         */
        public Builder allowSimple(boolean allowSimple) {
            this.allowSimple = Boolean.toString(allowSimple);
            return this;
        }

        /**
         * Whether to allow using the easy predicate parser to pre-parse predicates.
         */
        public Builder allowEasyPredicate(String allowEasyPredicate) {
            this.allowEasyPredicate = allowEasyPredicate;
            return this;
        }

        /**
         * Whether to allow using the easy predicate parser to pre-parse predicates.
         */
        public Builder allowEasyPredicate(boolean allowEasyPredicate) {
            this.allowEasyPredicate = Boolean.toString(allowEasyPredicate);
            return this;
        }

        /**
         * Whether to write the output of each row/element as a JSON String value instead of a Map/POJO value.
         */
        public Builder writeAsString(String writeAsString) {
            this.writeAsString = writeAsString;
            return this;
        }

        /**
         * Whether to write the output of each row/element as a JSON String value instead of a Map/POJO value.
         */
        public Builder writeAsString(boolean writeAsString) {
            this.writeAsString = Boolean.toString(writeAsString);
            return this;
        }

        /**
         * Whether to unpack a single element json-array into an object.
         */
        public Builder unpackArray(String unpackArray) {
            this.unpackArray = unpackArray;
            return this;
        }

        /**
         * Whether to unpack a single element json-array into an object.
         */
        public Builder unpackArray(boolean unpackArray) {
            this.unpackArray = Boolean.toString(unpackArray);
            return this;
        }

        /**
         * To configure additional options on JSONPath. Multiple values can be separated by comma.
         */
        public Builder option(String option) {
            this.option = option;
            return this;
        }

        /**
         * To configure additional options on JSONPath.
         */
        public Builder option(Option... options) {
            this.option = Arrays.stream(options).map(Objects::toString).collect(Collectors.joining(","));
            return this;
        }

        @Override
        public JsonPathExpression end() {
            return new JsonPathExpression(this);
        }
    }

    /**
     * {@code Option} defines the possible json path options that can be used.
     */
    @XmlTransient
    public enum Option {
        DEFAULT_PATH_LEAF_TO_NULL,
        ALWAYS_RETURN_LIST,
        AS_PATH_LIST,
        SUPPRESS_EXCEPTIONS,
        REQUIRE_PROPERTIES
    }
}
