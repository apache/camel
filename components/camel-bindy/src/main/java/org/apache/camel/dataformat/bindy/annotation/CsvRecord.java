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
 * This annotation represents the root class of the model. When a CSV,
 * fixed-length record must be described in the model we will use this
 * annotation and the separator (for csv record) to know how to split the data
 * during the unmarshal process The separator (mandatory) The name is optional
 * and could be used in the future to bind a property which a different name The
 * skipfirstline (optional) allows to skip the first line of the file/content
 * received The generateHeaderColumnNames (optional) allow to add in the CSV
 * generated the header containing names of the columns The crlf (optional) is
 * used to add a new line after a record. By default, the value is WINDOWS The
 * isOrdered (optional) boolean is used to ordered the message generated in
 * output
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface CsvRecord {

    /**
     * Name describing the record (optional)
     */
    String name() default "";

    /**
     * Separator used to split a record in tokens (mandatory)
     */
    String separator();

    /**
     * The skipFirstLine parameter will allow to skip or not the first line of a
     * CSV file. This line often contains columns definition
     */
    boolean skipFirstLine() default false;

    /**
     * The skipField parameter will allow to skip fields of a CSV file. 
     * If some fields are not necessary, they can be skipped.
     */
    boolean skipField() default false;

    /**
     * Character to be used to add a carriage return after each record
     * (optional) Three values can be used : WINDOWS, UNIX or MAC.
     */
    String crlf() default "WINDOWS";

    /**
     * The generateHeaderColumns parameter allow to add in the CSV generated the
     * header containing names of the columns
     */
    boolean generateHeaderColumns() default false;

    /**
     * Indicates if the message must be ordered in output
     */
    boolean isOrdered() default false;

    /**
     * Whether to marshal columns with the given quote character (optional)
     */
    String quote() default "\"";

    /**
     * Indicate if the values (and headers) must be quoted when marshaling (optional)
     */
    boolean quoting() default false;
    
    /**
     * Indicate if the values must be escaped when quoting (optional)
     */
    boolean quotingEscaped() default false;

    /**
     * Last record spans rest of line (optional)
     */
    boolean autospanLine() default false;
    
    /**
     * The allowEmptyStream parameter will allow to prcoess 
     * the unavaiable stream for CSV file.
     */
    boolean allowEmptyStream() default false;
    
    /**
     * The endWithLineBreak parameter flags if the CSV file should end with a line break or not (optional)
     */
    boolean endWithLineBreak() default true;

    /**
     * The remove quotes parameter flags if unmarshalling should try to remove quotes for each field
     */
    boolean removeQuotes() default true;

}
