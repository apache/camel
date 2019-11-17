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
 * mandatory. The lengthPos (optional) identifies a field in this record that 
 * defines the fixed length for this field.  The delimiter (optional) defines a
 * character that is used to demarcate the field, if it has a variable length.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface DataField {

    /**
     * Position of the data in the input record, must start from 1 (mandatory).
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
     * @return String timezone ID
     */
    String timezone() default "";

    /**
     * Length of the data block (number of characters) if the record is set to a fixed length
     */
    int length() default 0;
    
    /**
     * Identifies a data field in the record that defines the expected fixed length for this field
     */
    int lengthPos() default 0;
    
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
     * Position of the field in the output message generated (should start from 1)
     *
     * @see #pos()
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
    
    /**
     * Optional delimiter to be used if the field has a variable length
     */
    String delimiter() default "";
    
    /**
     * Field's default value in case no value is set 
     */
    String defaultValue() default "";

    /**
     * Indicates if there is a decimal point implied at a specified location
     */
    boolean impliedDecimalSeparator() default false;

    /**
     * Decimal Separator to be used with BigDecimal number
     */
    String decimalSeparator() default "";

    /**
     * Grouping Separator to be used with BigDecimal number
     * when we would like to format/parse to number with grouping
     * e.g. 123,456.789
     */
    String groupingSeparator() default "";

    /**
     * Round mode to be used to round/scale a BigDecimal
     * Values : UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN,HALF_EVEN, UNNECESSARY
     * e.g : Number = 123456.789, Precision = 2, Rounding =  CEILING
     * Result : 123456.79
     */
    String rounding() default "CEILING";

    /**
     * Method name to call to apply such customization
     * on DataField. This must be the method on the datafield
     * itself or you must provide static fully qualified name of
     * the class's method e.g: see unit test 
     * org.apache.camel.dataformat.bindy.csv.BindySimpleCsvFunctionWithExternalMethodTest.replaceToBar
     */
    String method() default ""; 
}
