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
package org.apache.camel.spi.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used for JAXB property (field or method) annotated with {@code @XmlAnyElement} to highlight which
 * actual elements do we expect (not to be enforced by JAXB, but by Camel itself).
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface ExternalSchemaElement {

    /**
     * Names of external XML element we expect
     *
     * @return
     */
    String[] names() default {};

    /**
     * XSD namespace of XML elements expected
     *
     * @return
     */
    String namespace() default "";

    /**
     * In JAXB, when an element is annotated with {@code @XmlAnyElement}, the actual objects used are of
     * {@link org.w3c.dom.Element} class. These elements should be part of wrapping {@link org.w3c.dom.Document} and
     * this parameter allows to specify this root element name (in {@link #namespace()}).
     *
     * @return
     */
    String documentElement();

}
