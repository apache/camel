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
package org.apache.camel.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One side of {@link BodyTypes}: the body a step needs or leaves.
 * <p/>
 * The carriers are coarse on purpose: a carrier that is right is better than a precise Java type that is wrong. Give
 * the {@link #javaType()} only when the body is always of that type.
 *
 * @since 4.24
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({})
public @interface BodyType {

    /**
     * The carriers of the body: the kinds of Java object it is in. Several for a body that can be any of them. None
     * when the side is not known.
     */
    Carrier[] value() default {};

    /**
     * The Java type of the body, when it is always of this type, such as
     * {@code org.apache.camel.component.file.GenericFile}.
     */
    String javaType() default "";

    /**
     * The format of the payload, when it is always the same: json, xml, csv, yaml, text or binary. These are the names
     * of the data types of Kamelets.
     */
    String format() default "";

    /**
     * The options the body depends on. The carriers hold while these options keep their default values; with another
     * value the body is not known, unless {@link #byOption()} says it.
     */
    String[] dependsOn() default {};

    /**
     * The body for other values of an option, such as {@code output=bytes}.
     */
    When[] byOption() default {};

    /**
     * What to know about the body, in a sentence for the documentation and the tools.
     */
    String description() default "";

    /**
     * The body when an option has a value.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Target({})
    @interface When {

        /**
         * The name of the option.
         */
        String option();

        /**
         * The value of the option.
         */
        String value();

        /**
         * The carriers of the body with this value.
         */
        Carrier[] carriers();

        /**
         * The Java type of the body with this value, when it is always of this type.
         */
        String javaType() default "";
    }

    /**
     * The kinds of Java object a body is in. The runtime sorts the bodies it records into kinds as well (null, payload,
     * data and collection): text, bytes, a stream, a file and a value are a payload, a map and a pojo are data, a list
     * and an iterator are a collection.
     */
    enum Carrier {

        /** No body. */
        NULL("null"),
        /** Text: a String. */
        TEXT("text"),
        /** A byte array. */
        BYTES("bytes"),
        /** A stream: an InputStream, a Reader, or the stream cache of Camel. */
        STREAM("stream"),
        /** A file: the GenericFile of the file consumers, a java.io.File or a Path. */
        FILE("file"),
        /** A single value: a number, a boolean or an enum. */
        VALUE("value"),
        /** A java.util.Map, such as a JSON object parsed or a row of a query. */
        MAP("map"),
        /** A List, another Collection or an array, such as a JSON array parsed or the rows of a query. */
        LIST("list"),
        /**
         * An Iterator, which is read once, element by element, and has no size and no index: the rows of a query that
         * streams them, or of a CSV file read lazily.
         */
        ITERATOR("iterator"),
        /** Another Java object, such as a DOM Document or a class a payload is unmarshalled into. */
        POJO("pojo"),
        /** The body as it was before the step: for the body after a step only. */
        UNCHANGED("unchanged"),
        /** Any body: for the bodies a step needs only. */
        ANY("any"),
        /**
         * The body depends on what the other system sends, or on a class the user configures: tooling must not guess
         * it.
         */
        UNKNOWN("unknown");

        private final String value;

        Carrier(String value) {
            this.value = value;
        }

        /**
         * The name of the carrier, as the catalog has it.
         */
        public String getValue() {
            return value;
        }
    }
}
