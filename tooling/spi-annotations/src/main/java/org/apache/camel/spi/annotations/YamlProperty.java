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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface YamlProperty {
    String name();

    String type();

    String defaultValue() default "";

    String format() default "";

    String displayName() default "";

    String description() default "";

    boolean deprecated() default false;

    boolean required() default false;

    String[] values() default {};

    String oneOf() default "";

    boolean wrapItem() default false;

    /**
     * The name of the property that identifies an item of this list (such as name). When set, the list may also be
     * written as a map from that property to the rest of the item, which the non-canonical schema accepts. Only the
     * YAML schema generator reads this attribute: the deserializer of the property that carries it must itself accept
     * the map form (as BeansDeserializer.asBeanDefinitions does for beans:).
     *
     * @since 4.23
     */
    String mapKey() default "";
}
