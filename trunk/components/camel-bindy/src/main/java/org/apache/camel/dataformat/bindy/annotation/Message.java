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
 * This annotation represents the root class of the model. When a message (FIX
 * message containing key-value pairs) must be described in the model, we will
 * use this annotation. The key pair separator (mandatory) defines the separator
 * between the key and the value The pair separator (mandatory) allows to define
 * which character separate the pairs from each other The name is optional and
 * could be used in the future to bind a property which a different name The
 * type (optional) allow to define the type of the message (e.g. FIX, EMX, ...)
 * The version (optional) defines the version of the message (e.g. 4.1, ...) The
 * crlf (optional) is used to add a new line after a record. By default, the
 * value is WINDOWS The isOrdered (optional) boolean is used to ordered the
 * message generated in output (line feed and carriage return on windows
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Message {

    /**
     * Name describing the message (optional)
     * 
     * @return String
     */
    String name() default "";

    /**
     * Pair separator used to split the key value pairs in tokens (mandatory)
     * 
     * @return String
     */
    String pairSeparator();

    /**
     * Key value pair separator is used to split the values from their keys
     * (mandatory)
     * 
     * @return String
     */
    String keyValuePairSeparator();

    /**
     * type is used to define the type of the message (e.g. FIX, EMX, ...)
     * (optional)
     */
    String type() default "FIX";

    /**
     * version defines the version of the message (e.g. 4.1, ...) (optional)
     */
    String version() default "4.1";

    /**
     * Character to be used to add a carriage return after each record
     * (optional) Three values can be used : WINDOWS, UNIX or MAC
     * 
     * @return String
     */
    String crlf() default "WINDOWS";

    /**
     * Indicates if the message must be ordered in output
     * 
     * @return boolean
     */
    boolean isOrdered() default false;
}
