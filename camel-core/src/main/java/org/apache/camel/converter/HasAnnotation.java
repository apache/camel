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
package org.apache.camel.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to indicate that the actual type of a parameter on a converter method must have the given annotation class
 * to be applicable. e.g. this annotation could be used on a JAXB converter which only applies to objects with a
 * JAXB annotation on them
 *
 * @version
 * @deprecated not in use, will be removed in next Camel release.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.PARAMETER })
@Deprecated
public @interface HasAnnotation {

    Class<?> value();
}
