/*
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
 * Used to indicate an injection point of an {@link Endpoint}, {@link Producer} or {@link ProducerTemplate} into a POJO.
 *
 * A <a href="http://camel.apache.org/uris.html">URI</a> for an endpoint can be specified on this annotation, or a name
 * can be specified which is resolved in the {@link org.apache.camel.spi.Registry} such as in your Spring
 * ApplicationContext.
 *
 * If no ref or uri is specified then the ref is defaulted from the field, property or method name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface EndpointInject {

    /**
     * The uri of the endpoint
     */
    String value() default "";

    /**
     * Use the field or getter on the bean to provide the uri of the endpoint
     */
    String property() default "";

}
