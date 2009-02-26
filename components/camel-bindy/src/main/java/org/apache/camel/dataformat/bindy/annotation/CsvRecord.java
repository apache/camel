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
 * This annotation represents the root class of the model. When a CSV,
 * fixed-length record must be described in the model we will use this
 * annotation and the separator (for csv record) to know how to split the data
 * during the unmarshal process
 * The separator (mandatory)
 * The name is optional and could be used in the future to bind a property which a different name
 * The skipfirstline (optional) allows to skip the first line of the file/content received
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface CsvRecord {

    /**
     * Name describing the record (optional)
     * 
     * @return String
     */
    String name() default "";

    /**
     * Separator used to split a record in tokens (mandatory)
     * 
     * @return String
     */
    String separator();

    /**
     * The skipFirstLine parameter will allow to skip or not the first line of a
     * CSV file. This line often contains columns definition
     * 
     * @return boolean
     */
    boolean skipFirstLine() default false;

}
