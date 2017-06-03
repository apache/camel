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
 * An annotation used to identify in a POJO which property is link to a key
 * value pair field The tag (mandatory) identifies the key of the key value pair
 * (e.g. 8 equals the begin string in FIX The name (optional) could be used in
 * the future to bind a property which a different name The pattern (optional)
 * allows to define the pattern of the data (useful for Date, BigDecimal ...)
 * The precision (optional) reflects the precision to be used with BigDecimal
 * number The required (optional) field allows to define if the field is
 * required or not. This property is not yet used but will be useful in the
 * future with the validation The position (optional) field is used to order the
 * tags during the creation of the message
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface KeyValuePairField {

    /**
     * tag identifying the field in the message (mandatory)
     * 
     * @return int
     */
    int tag();

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
     * @return String timezone ID
     */
    String timezone() default "";

    /**
     * Position of the field in the message generated
     * 
     * @return int
     */
    int position() default 0;

    /**
     * precision of the BigDecimal number to be created
     * 
     * @return int
     */
    int precision() default 0;

    /**
     * Indicates if the field is mandatory
     */
    boolean required() default false;

    /**
     * Indicates if there is a decimal point implied at a specified location
     */
    boolean impliedDecimalSeparator() default false;
}
