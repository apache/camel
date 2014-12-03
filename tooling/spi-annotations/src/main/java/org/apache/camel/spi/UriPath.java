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
package org.apache.camel.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents an injection point of a Camel Uri path value (the remaining part of a Camel URI without any query arguments)
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD })
public @interface UriPath {

    /**
     * Returns the name of the uri path.
     * <p/>
     * This can be used to name the uri path something meaningful, such as a <tt>directory</tt>, <tt>queueName</tt> etc.
     * <p/>
     * If this is not specified then the name of the field or property which has this annotation is used.
     */
    String name() default "";

    /**
     * Returns a description of this uri path
     * <p/>
     * This is used for documentation and tooling only.
     */
    String description() default "";

}