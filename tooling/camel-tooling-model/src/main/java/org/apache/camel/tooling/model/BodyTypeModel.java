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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One side of the body types of a component, data format, language or EIP: the body a step needs, or the body it leaves
 * after it, with the default options (CAMEL-24961, CAMEL-25928).
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
    /** The bodies an EIP needs. */
    public static final String EIP_IN = "eipIn";
    /** The body after an EIP. */
    public static final String EIP_OUT = "eipOut";
    /** The body the steps of an EIP start with. */
    public static final String EIP_STEPS = "eipSteps";
    /** The value of {@link #getFrom()} for the body the steps of an EIP end with. */
    public static final String FROM_STEPS = "steps";

    /**
     * The carriers of the classes a type option often names, by name, the short names Camel takes as well: java.lang
     * without the package, and the primitives.
     */
    private static final Map<String, String> CARRIERS = carriers();

    private List<String> carriers = new ArrayList<>();
    private String javaType;
    private String format;
    private List<String> dependsOn = new ArrayList<>();
    private List<OptionBodyTypeModel> byOption = new ArrayList<>();
    private String from;
    private String javaTypeOption;
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
     * The side of another model the body comes from, for an EIP: such as marshalOut, the side of its data format, or
     * steps, the body its own steps end with. Null when the carriers say the body.
     */
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * The option that names the Java type of the body, such as resultType of a language: when it is set, and the
     * options of {@link #getDependsOn()} keep their defaults, the body is of the class it names, or null, and the
     * bodies of {@link #getByOption()} for other options do not hold. The carriers say the body when it is not set.
     */
    public String getJavaTypeOption() {
        return javaTypeOption;
    }

    public void setJavaTypeOption(String javaTypeOption) {
        this.javaTypeOption = javaTypeOption;
    }

    /**
     * The carrier of a body of a Java type, such as a type option names: text for String, bytes for byte[], stream,
     * file, value, map, list (a Collection or an array) and iterator, unknown for Object, which can be anything, and
     * pojo for any other class. The type is a class name, with or without generics, or a short name Camel takes, such
     * as String or int.
     */
    public static String carrierOf(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            return "unknown";
        }
        String name = javaType.trim();
        int generic = name.indexOf('<');
        if (generic > 0) {
            name = name.substring(0, generic).trim();
        }
        String answer = CARRIERS.get(name);
        if (answer != null) {
            return answer;
        }
        if (name.endsWith("[]")) {
            return "list";
        }
        return "pojo";
    }

    private static Map<String, String> carriers() {
        Map<String, String> answer = new HashMap<>();
        for (String name : List.of("String", "java.lang.String", "java.lang.CharSequence", "java.lang.StringBuilder")) {
            answer.put(name, "text");
        }
        // java.lang.byte[] is a name Camel accepts for byte[] (ObjectHelper.loadSimpleType), as in convertBodyTo
        for (String name : List.of("byte[]", "java.lang.byte[]")) {
            answer.put(name, "bytes");
        }
        for (String name : List.of("java.io.InputStream", "java.io.ByteArrayInputStream", "java.io.Reader",
                "org.apache.camel.StreamCache")) {
            answer.put(name, "stream");
        }
        for (String name : List.of("java.io.File", "java.nio.file.Path", "org.apache.camel.WrappedFile",
                "org.apache.camel.component.file.GenericFile")) {
            answer.put(name, "file");
        }
        for (String name : List.of("Boolean", "Integer", "Long", "Short", "Byte", "Float", "Double", "Character",
                "Number")) {
            answer.put(name, "value");
            answer.put("java.lang." + name, "value");
        }
        for (String name : List.of("boolean", "int", "long", "short", "byte", "float", "double", "char",
                "java.math.BigDecimal", "java.math.BigInteger")) {
            answer.put(name, "value");
        }
        for (String name : List.of("java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
                "java.util.Properties")) {
            answer.put(name, "map");
        }
        for (String name : List.of("java.util.Collection", "java.util.List", "java.util.ArrayList",
                "java.util.LinkedList", "java.util.Set", "java.util.HashSet", "java.util.LinkedHashSet",
                "java.util.TreeSet")) {
            answer.put(name, "list");
        }
        answer.put("java.util.Iterator", "iterator");
        answer.put("Object", "unknown");
        answer.put("java.lang.Object", "unknown");
        return Map.copyOf(answer);
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
        return (from == null && carriers.isEmpty()) || carriers.contains("unknown");
    }

    @Override
    public String toString() {
        String answer = String.join(", ", carriers) + (javaType != null ? " (" + javaType + ")" : "");
        if (from != null) {
            answer = answer.isEmpty() ? "from:" + from : answer + " (from:" + from + ")";
        }
        if (javaTypeOption != null) {
            answer = answer + " (javaTypeOption:" + javaTypeOption + ")";
        }
        return answer;
    }

    /**
     * The body when an option has a value.
     */
    public static class OptionBodyTypeModel {

        private String option;
        private String value;
        private List<String> carriers = new ArrayList<>();
        private String javaType;
        private String javaTypeOption;

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

        /**
         * The option that names the Java type of the body with this value: when it is set, the body is of the class it
         * names, or null, and the carriers say the body when it is not set.
         */
        public String getJavaTypeOption() {
            return javaTypeOption;
        }

        public void setJavaTypeOption(String javaTypeOption) {
            this.javaTypeOption = javaTypeOption;
        }

        @Override
        public String toString() {
            return option + "=" + value + ": " + String.join(", ", carriers)
                   + (javaTypeOption != null ? " (javaTypeOption:" + javaTypeOption + ")" : "");
        }
    }
}
