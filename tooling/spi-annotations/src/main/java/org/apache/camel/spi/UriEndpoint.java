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
 * Represents an annotated Camel <a href="http://camel.apache.org/endpoint.html">Endpoint</a>
 * which can have its properties (and the properties on its consumer) injected from the
 * Camel URI path and its query parameters
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE})
public @interface UriEndpoint {

    /**
     * Represents the URI scheme name of this endpoint.
     * <p/>
     * Multiple scheme names can be defined as a comma separated value.
     * For example to associate <tt>http</tt> and <tt>https</tt> to the same endpoint implementation.
     */
    String scheme();

    /**
     * Represents the consumer class which is injected and created by consumers
     */
    Class<?> consumerClass() default Object.class;

    /**
     * The configuration parameter name prefix used on parameter names to separate the endpoint
     * properties from the consumer properties
     */
    String consumerPrefix() default "";

    /**
     * To associate this endpoint with label(s).
     * <p/>
     * Multiple labels can be defined as a comma separated value.
     * <p/>
     * The labels is intended for grouping the endpoints, such as <tt>core</tt>, <tt>file</tt>, <tt>messaging</tt>, <tt>database</tt>, etc.
     */
    String label() default "";

    /**
     * Whether this endpoint can only be used as a producer.
     * <p/>
     * By default its assumed the endpoint can be used as both consumer and producer.
     */
    boolean producerOnly() default false;

    /**
     * Whether this endpoint can only be used as a consumer.
     * <p/>
     * By default its assumed the endpoint can be used as both consumer and producer.
     */
    boolean consumerOnly() default false;

}