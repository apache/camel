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
 * This annotation represents the root class of the model. When a 
 * fixed-length record must be described in the model we will use this
 * annotation to split the data during the unmarshal process.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface FixedLengthRecord {

    /**
     * Name describing the record (optional)
     * 
     * @return String
     */
    String name() default "";

    /**
     * Character to be used to add a carriage return after each record
     * (optional) Three values can be used : WINDOWS, UNIX or MAC
     * 
     * @return String
     */
    String crlf() default "WINDOWS";
    
    /**
     * The char to pad with.
     * @return the char to pad with if the record is set to a fixed length;
     */
    char paddingChar() default ' ';
    
    /**
     * The fixed length of the record. It means that the record will always be that long padded with {#paddingChar()}'s
     * @return the length of the record.
     */
    int length() default 0;

    boolean hasHeader() default false;
    
    boolean hasFooter() default false;

}
