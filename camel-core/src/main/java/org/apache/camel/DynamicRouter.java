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
 * Indicates that this method is to be used as a 
 * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router</a> routing the incoming message
 * through a series of processing steps.
 *
 * When a message {@link Exchange} is received from an {@link Endpoint} then the
 * <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>
 * mechanism is used to map the incoming {@link Message} to the method parameters.
 *
 * The return value of the method is then converted to either a {@link java.util.Collection} or array of objects where each
 * element is converted to an {@link org.apache.camel.Endpoint} or a {@link String}, or if it is not a collection/array then it is converted
 * to an {@link org.apache.camel.Endpoint} or {@link String}.
 *
 * Then for each endpoint or URI the message is routed in a pipes and filter fashion.
 *
 * @see org.apache.camel.RoutingSlip
 * @version 
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface DynamicRouter {

    /**
     * Id of {@link CamelContext} to use
     */
    String context() default "";

    /**
     * Sets the uri delimiter to use
     */
    String delimiter() default ",";

    /**
     * Whether to ignore the invalidate endpoint exception when try to create a producer with that endpoint
     */
    boolean ignoreInvalidEndpoints() default false;

    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ProducerCache} which is used
     * to cache and reuse producers when using this dynamic router, when uris are reused.
     */
    int cacheSize() default 0;
}
