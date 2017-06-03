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
     * The first version this endpoint was added to Apache Camel.
     */
    String firstVersion() default "";

    /**
     * Represents the URI scheme name of this endpoint.
     * <p/>
     * Multiple scheme names can be defined as a comma separated value.
     * For example to associate <tt>http</tt> and <tt>https</tt> to the same endpoint implementation.
     * <p/>
     * The order of the scheme names here should be the same order as in {@link #extendsScheme()} so their are paired.
     */
    String scheme();

    /**
     * Used when an endpoint is extending another endpoint
     * <p/>
     * Multiple scheme names can be defined as a comma separated value.
     * For example to associate <tt>ftp</tt> and <tt>ftps</tt> to the same endpoint implementation.
     * The order of the scheme names here should be the same order as in {@link #scheme()} so their are paired.
     */
    String extendsScheme() default "";

    /**
     * Represent the URI syntax the endpoint must use.
     * <p/>
     * The syntax follows the patterns such as:
     * <ul>
     *     <li>scheme:host:port</li>
     *     <li>scheme:host:port/path</li>
     *     <li>scheme:path</li>
     *     <li>scheme:path/path2</li>
     * </ul>
     * Where each path maps to the name of the endpoint {@link org.apache.camel.spi.UriPath} option.
     * The query parameters is implied and should not be included in the syntax.
     * <p/>
     * Some examples:
     * <ul>
     *     <li>file:directoryName</li>
     *     <li>ftp:host:port/directoryName</li>
     *     <li>jms:destinationType:destinationName</li>
     * </ul>
     */
    String syntax();

    /**
     * If the endpoint supports specifying username and/or password in the UserInfo part of the URI, then the
     * alternative syntax can represent this such as:
     * <ul>
     *     <li>ftp:userName:password@host:port/directoryName</li>
     *     <li>ssh:username:password@host:port</li>
     * </ul>
     */
    String alternativeSyntax() default "";

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
     * A human readable title of this entity, such as the component name of the this endpoint.
     * <p/>
     * For example: JMS, MQTT, Netty HTTP, SAP NetWeaver
     */
    String title();

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

    /**
     * Should all properties be known or does the endpoint allow unknown options?
     * <p/>
     * <tt>lenient = false</tt> means that the endpoint should validate that all
     * given options is known and configured properly.
     * <tt>lenient = true</tt> means that the endpoint allows additional unknown options to
     * be passed to it but does not throw a ResolveEndpointFailedException when creating
     * the endpoint.
     * <p/>
     * This options is used by a few components for instance the HTTP based that can have
     * dynamic URI options appended that is targeted for an external system.
     * <p/>
     * Most endpoints is configured to be <b>not</b> lenient.
     */
    boolean lenientProperties() default false;

    /**
     * To exclude one or more properties in this endpoint.
     * <p/>
     * This is used when a Camel component extend another component, and then may need to not use some of the properties from
     * the parent component. Multiple properties can be separated by comma.
     */
    String excludeProperties() default "";

}