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
package org.apache.camel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation used to mark classes and methods to indicate code capable of
 * converting from a type to another type which are then auto-discovered using
 * the <a href="http://camel.apache.org/type-converter.html">Type
 * Conversion Support</a>
 * 
 * @version 
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.METHOD })
public @interface Converter {

    /**
     * Whether or not returning <tt>null</tt> is a valid response.
     */
    boolean allowNull() default false;

    /**
     * Whether to ignore the type converter if it cannot be loaded for some reason.
     * <p/>
     * This can be used if a Camel component provides multiple components
     * where the end user can opt-out some of these components by excluding
     * dependencies on the classpath, meaning the type converter would not
     * be able to load due class not found errors. But in those cases its
     * okay as the component is opted-out.
     * <p/>
     * Important this configuration must be set on the class-level, not on the method.
     */
    boolean ignoreOnLoadError() default false;
}
