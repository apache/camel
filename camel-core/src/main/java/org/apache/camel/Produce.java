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
package org.apache.camel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or property as being a producer to an {@link org.apache.camel.Endpoint} either via its
 * <a href="http://camel.apache.org/uris.html">URI</a> or via the name of the endpoint reference
 * which is then resolved in a registry such as the Spring Application Context.
 * <p/>
 * Methods invoked on the producer object are then converted to a message {@link org.apache.camel.Exchange} via the
 * <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>
 * mechanism.
 *
 * @see InOnly
 *
 * @version 
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface Produce {

    /**
     * The uri to produce to
     */
    String uri() default "";

    /**
     * Reference to endpoint to produce to
     */
    String ref() default "";

    /**
     * Use the field or getter on the bean to provide the uri to produce to
     */
    String property() default "";

    /**
     * Id of {@link CamelContext} to use
     */
    String context() default "";

    /**
     * Whether to use bean parameter binding
     */
    boolean binding() default true;
}