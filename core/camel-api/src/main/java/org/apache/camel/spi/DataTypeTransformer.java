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
 * Annotation to configure a data type transformer with either specifying its name or from/to data types.
 * <p/>
 * The annotation is used by specific classpath scanning data type loaders to automatically add the data types to a
 * registry.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.TYPE })
public @interface DataTypeTransformer {

    /**
     * Data type transformer name. Identifies the data type transformer. Should be unique in the Camel context. Can be a
     * combination of scheme and name. Is used to detect/reference the transformer when specifying input/output data
     * types on routes.
     *
     * @return the data type transformer name.
     */
    String name() default "";

    /**
     * Data type representing the input of the transformation. Also used to detect the transformer.
     *
     * @return
     */
    String fromType() default "";

    /**
     * Data type representing the result of the transformation. Also used to detect the transformer.
     *
     * @return
     */
    String toType() default "";

}
