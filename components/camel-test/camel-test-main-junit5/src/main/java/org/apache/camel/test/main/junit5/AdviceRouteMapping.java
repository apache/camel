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
package org.apache.camel.test.main.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.camel.builder.RouteBuilder;

/**
 * A specific annotation allowing to define a mapping between a route represented by its id and advice represented by a
 * subclass of {@link RouteBuilder} to use to advice the route. To advice a route, an instance of {@link RouteBuilder}
 * needs to be created and for this the default constructor is used, so make sure that a default constructor exists.
 * <p/>
 * This annotation is only meant to be used inside the annotation {@link CamelMainTest} to define all the advices to
 * consider.
 * <p/>
 * In the next example, the annotation {@code CamelMainTest} on the test class {@code SomeTest} indicates that the
 * {@code RouteBuilder} of type {@code ReplaceDirectWithMockBuilder} should be used to advise the route whose identifier
 * is <i>main-route</i>.
 *
 * <pre>
 * <code>
 *
 * &#64;CamelMainTest(advices = @AdviceRouteMapping(route = "main-route", advice = ReplaceDirectWithMockBuilder.class))
 * class SomeTest {
 *     // The rest of the test class
 * }
 * </code>
 * </pre>
 *
 * @see RouteBuilder
 * @see CamelMainTest
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface AdviceRouteMapping {

    /**
     * @return the id of the route to <i>advice</i>.
     */
    String route();

    /**
     * @return the class of the specific route builder that is used to advice the route.
     */
    Class<? extends RouteBuilder> advice();
}
