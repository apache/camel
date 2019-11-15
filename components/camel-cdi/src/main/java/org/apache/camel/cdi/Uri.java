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
package org.apache.camel.cdi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;

/**
 * A CDI qualifier to define the <a href="http://camel.apache.org/uris.html">Camel URI</a> associated to
 * the annotated resource. This annotation can be used to annotate an {@code @Inject} injection point for
 * values of type {@link org.apache.camel.Endpoint} or {@link org.apache.camel.ProducerTemplate}. For example:
 * <pre><code>
 * {@literal @}Inject
 * {@literal @}Uri("mock:foo")
 * Endpoint endpoint;
 *
 * {@literal @}Inject
 * {@literal @}Uri("seda:bar")
 * ProducerTemplate producer;
 * </code></pre>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface Uri {

    /**
     * Returns the <a href="http://camel.apache.org/uris.html">Camel URI</a> of the resource.
     */
    @Nonbinding String value();

    final class Literal extends AnnotationLiteral<Uri> implements Uri {

        private static final long serialVersionUID = 1L;

        private final String uri;

        private Literal(String uri) {
            this.uri = uri;
        }

        public static Literal of(String uri) {
            return new Literal(uri);
        }

        @Override
        public String value() {
            return uri;
        }

    }
}
