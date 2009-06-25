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
 * a record (csv, ...).
 * The pos (mandatory) identifies the position of the data in the record
 * The name is optional and could be used in the future to bind a property which a different name
 * The pattern (optional) allows to define the pattern of the data (useful for Date, ...)
 * The length (optional) allows to define for fixed length message the size of the data's block
 * The precision(optional) reflects the precision to be used with BigDecimal number
 * The position (optional) identify the position of the field in the CSV generated
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface DataField {

    /**
     * position of the data in the record (mandatory)
     * 
     * @return int
     */
    int pos();

    /**
     * name of the field (optional)
     * 
     * @return String
     */
    String name() default "";

    /**
     * pattern that the formater will use to transform the data (optional)
     * 
     * @return String
     */
    String pattern() default "";

    /**
     * length of the data block (useful for the fixedlength record) (optional in
     * this version)
     * 
     * @return int
     */
    int length() default 0;

    /**
     * precision of the BigDecimal number to be created
     * 
     * @return int
     */
    int precision() default 0;
    
    /**
     * 
     * Position of the field in the message generated
     * 
     * @return int 
     */
    int position() default 0;

}
