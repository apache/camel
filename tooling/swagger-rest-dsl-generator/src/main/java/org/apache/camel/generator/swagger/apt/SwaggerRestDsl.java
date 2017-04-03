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
package org.apache.camel.generator.swagger.apt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.generator.swagger.DestinationGenerator;
import org.apache.camel.generator.swagger.DirectToOperationId;

/**
 * Annotation used to generate REST DSL definitions from Swagger specification.
 */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target(ElementType.TYPE)
public @interface SwaggerRestDsl {

    /**
     * Name of the class to generate the REST DSL {@link RouteBuilder}
     * implementation. By default it's based on the Swagger specification: using
     * the title from the info.
     */
    String className() default "";

    /**
     * Destination generator that maps Swagger operation definition to route
     * definition. By default maps to {@code "direct:<operationId>"}.
     */
    Class<? extends DestinationGenerator> destinationGenerator() default DirectToOperationId.class;

    /**
     * Name of the package to generate the REST DSL {@link RouteBuilder}
     * implementation. By default it's the package on of the class or package
     * this annotation is present on.
     */
    String packageName() default "";

    /**
     * URI to the Swagger specification. If not set looks for `swagger.json` or
     * `swagger.yml`.
     */
    String specificationUri() default "";

    /**
     * Shorthand for {@link #specificationUri()} value, path to the Swagger
     * specification. If not set looks for `swagger.json` or `swagger.yml`.
     */
    String value() default "";
}
