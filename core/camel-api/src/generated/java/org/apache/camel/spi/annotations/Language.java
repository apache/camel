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

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.TYPE })
@ServiceFactory("language")
public @interface Language {

    String value();

    /**
     * The class that contains all the name of functions that are supported by the language. The name of the functions
     * are defined as {@code String} constants in the functions class.
     *
     * The class to provide can be any class but by convention, we would expect a class whose name is of type
     * <i>xxxConstants</i> where <i>xxx</i> is the name of the corresponding language like for example
     * <i>SimpleConstants</i> for the language <i>camel-simple</i>.
     *
     * The metadata of a given functions are retrieved directly from the annotation {@code @Metadata} added to the
     * {@code String} constant representing its name and defined in the functions class.
     */
    Class<?> functionsClass() default void.class;

}
