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
package org.apache.camel.test.cdi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.builder.AdviceWithRouteBuilder;

/**
 * A special annotation meant for the class of type {@code AdviceWithRouteBuilder} in order to provide the id of the
 * route to <i>advice</i> but also to indicate that the start-up of the Camel context needs to be delayed to
 * <i>advice</i> properly the route.
 * <p/>
 * In the next example, the {@code AdviceWithRouteBuilder} of type {@code ReplaceDirectWithMockBuilder} advises the
 * route whose identifier is <i>main-route</i>.
 *
 * <pre>
 * <code>
 *
 * &#64;AdviceRoute("main-route")
 * public class ReplaceDirectWithMockBuilder extends AdviceWithRouteBuilder {
 *
 *     &#64;Override
 *     public void configure() throws Exception {
 *         weaveByToUri("direct:out").replace().to("mock:test");
 *     }
 * }
 * </code>
 * </pre>
 *
 * @see AdviceWithRouteBuilder
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AdviceRoute {

    /**
     * @return the id of the route to <i>advice</i>.
     */
    String value();
}
