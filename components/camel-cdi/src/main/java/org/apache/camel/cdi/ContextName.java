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
package org.apache.camel.cdi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * CDI qualifier to be used for multi Camel contexts CDI deployment.
 * {@code CamelContext} beans can be annotated with the {@code @ContextName} qualifier
 * so that the Camel context is named accordingly, e.g.:
 *
 * <pre><code>
 * {@literal @}ApplicationScoped
 * {@literal @}ContextName("foo")
 * public class FooCamelContext extends DefaultCamelContext {
 * }
 * </code></pre>
 *
 * Camel resources like route builders, endpoints and producer templates can be annotated with
 * the {@code @ContextName} qualifier as well so that they are associated with the
 * corresponding Camel context, e.g.:
 *
 * <pre><code>
 * {@literal @}ContextName("foo")
 * public class FooRouteBuilder extends RouteBuilder {
 *
 *     {@literal @}Override
 *     public void configure() {
 *         from("direct:bar").to("mock:bar");
 *     }
 * }
 *
 * {@literal @}Inject
 * {@literal @}ContextName("foo")
 * {@literal @}Uri("direct:bar")
 * ProducerTemplate barProducer;
 *
 * {@literal @}Inject
 * {@literal @}ContextName("foo")
 * {@literal @}Uri("mock:bar")
 * MockEndpoint barMockEndpoint;
 * </code></pre>
 *
 * @see org.apache.camel.CamelContext
 *
 */
@Qualifier
@Repeatable(ContextNames.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface ContextName {

    /**
     * Returns the name of the Camel context.
     */
    String value();

    final class Literal extends AnnotationLiteral<ContextName> implements ContextName {

        private static final long serialVersionUID = 1L;

        private final String name;

        private Literal(String name) {
            this.name = name;
        }

        public static Literal of(String name) {
            return new Literal(name);
        }

        @Override
        public String value() {
            return name;
        }
    }
}
