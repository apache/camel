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
 * One side of the body types of a component, data format or language: the body a step needs, or the body it leaves
 * after it, with the default options (CAMEL-24961).
 * <p/>
 * The carriers are the kinds of Java object the body is in: null, text, bytes, stream, file, value, map, list, iterator
 * and pojo; unchanged for a step that leaves the body as it was, any for a step that takes any body, and unknown for a
 * body that depends on what the other system sends or on a class the user configures.
 */
public class BodyTypeModel {

    /** The body of the message the consumer creates. */
    public static final String CONSUMER_OUT = "consumerOut";
    /** The bodies a consumer that answers the caller can reply with: the body the route ends with. */
    public static final String CONSUMER_REPLY = "consumerReply";
    /** The bodies the producer can send or write. */
    public static final String PRODUCER_IN = "producerIn";
    /** The body after the producer. */
    public static final String PRODUCER_OUT = "producerOut";
    /** The bodies a data format can marshal. */
    public static final String MARSHAL_IN = "marshalIn";
    /** The body after marshal. */
    public static final String MARSHAL_OUT = "marshalOut";
    /** The bodies a data format can unmarshal. */
    public static final String UNMARSHAL_IN = "unmarshalIn";
    /** The body after unmarshal. */
    public static final String UNMARSHAL_OUT = "unmarshalOut";
    /** The bodies the expressions of a language can read. */
    public static final String EXPRESSION_IN = "expressionIn";
    /** The result of an expression of a language. */
    public static final String EXPRESSION_OUT = "expressionOut";

    private List<String> carriers = new ArrayList<>();
    private String javaType;
    private String format;
    private List<String> dependsOn = new ArrayList<>();
    private List<OptionBodyTypeModel> byOption = new ArrayList<>();
    private String description;

    /**
     * The carriers of the body: one, or several for a body that can be any of them.
     */
    public List<String> getCarriers() {
        return carriers;
    }

    public void setCarriers(List<String> carriers) {
        this.carriers = carriers;
    }

    /**
     * The Java type of the body, when it is always of this type.
     */
    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    /**
     * The format of the payload, when it is always the same: json, xml, csv, yaml, text or binary.
     */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * The options the body depends on: with another value than the default the body is not known, unless
     * {@link #getByOption()} says it.
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    /**
     * The body for other values of an option.
     */
    public List<OptionBodyTypeModel> getByOption() {
        return byOption;
    }

    public void setByOption(List<OptionBodyTypeModel> byOption) {
        this.byOption = byOption;
    }

    /**
     * What to know about the body, in a sentence.
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Whether the body is left as it was (for the body after a step).
     */
    public boolean isUnchanged() {
        return carriers.contains("unchanged");
    }

    /**
     * Whether the body is not known without the configuration or the data of the other system. A side with no carriers
     * is not known either: the build rejects one, but a catalog from elsewhere, such as JSON written by hand, can have
     * it.
     */
    public boolean isUnknown() {
        return carriers.isEmpty() || carriers.contains("unknown");
    }

    @Override
    public String toString() {
        return String.join(", ", carriers) + (javaType != null ? " (" + javaType + ")" : "");
    }

    /**
     * The body when an option has a value.
     */
    public static class OptionBodyTypeModel {

        private String option;
        private String value;
        private List<String> carriers = new ArrayList<>();
        private String javaType;

        public String getOption() {
            return option;
        }

        public void setOption(String option) {
            this.option = option;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public List<String> getCarriers() {
            return carriers;
        }

        public void setCarriers(List<String> carriers) {
            this.carriers = carriers;
        }

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        @Override
        public String toString() {
            return option + "=" + value + ": " + String.join(", ", carriers);
        }
    }
}
