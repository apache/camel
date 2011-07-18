/**
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
package org.apache.camel.dataformat.bindy.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation used to identify in a POJO which property is link to a field of
 * a record (csv, ...). The pos (mandatory) identifies the position of the data
 * in the record The name is optional and could be used in the future to bind a
 * property which a different name The columnName (optional) represents the name
 * of the column who will appear in the header The pattern (optional) allows to
 * define the pattern of the data (useful for Date, ...) The length (optional)
 * allows to define for fixed length message the size of the data's block The
 * precision(optional) reflects the precision to be used with BigDecimal number
 * The position (optional) identify the position of the field in the CSV
 * generated The required (optional) property identifies a field which is
 * mandatory.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface DataField {

    /**
     * Position of the data in the record, must start from 1 (mandatory).
     */
    int pos();

    /**
     * Name of the field (optional)
     */
    String name() default "";

    /**
     * Name of the header column (optional)
     */
    String columnName() default "";

    /**
     * Pattern that the formatter will use to transform the data (optional)
     */
    String pattern() default "";

    /**
     * Length of the data block if the record is set to a fixed length
     */
    int length() default 0;
    
    /**
     * Align the text to the right or left. Use values <tt>R</tt> or <tt>L</tt>.
     */
    String align() default "R";
    
    /**
     * The char to pad with if the record is set to a fixed length
     */
    char paddingChar() default ' ';

    /**
     * precision of the {@link java.math.BigDecimal} number to be created
     */
    int precision() default 0;

    /**
     * Position of the field in the message generated (should start from 1)
     */
    int position() default 0;

    /**
     * Indicates if the field is mandatory
     */
    boolean required() default false;

    /**
     * Indicates if the value should be trimmed
     */
    boolean trim() default false;

    /**
     * Indicates to clip data in the field if it exceeds the allowed length when using fixed length.
     */
    boolean clip() default false;
}
