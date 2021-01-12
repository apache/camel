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
package org.apache.camel.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents an API method as part of a parent API.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.FIELD })
public @interface ApiMethod {

    /**
     * The name of the api method.
     */
    String methodName();

    /**
     * Returns the method signature(s) of this api method. A method may have one or more signatures due to overloaded
     * methods.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String[] signatures() default "";

    /**
     * Returns a description of this api method or api parameter.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String description() default "";

}
