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

import org.apache.camel.spi.annotations.ServiceFactory;

/**
 * Marks a {@link Transformer} implementation as a data type transformer, identified by its name and/or its from/to
 * {@link DataType}s.
 * <p/>
 * Classpath-scanning transformer loaders discover annotated classes and register them in the
 * {@link TransformerRegistry}, so they can be detected and applied when a route declares input/output data types via a
 * {@link Contract}. Provide a unique {@link #name()}, or a {@link #fromType()}/{@link #toType()} pair, so the
 * transformer can be referenced or auto-detected.
 * <p/>
 * See <a href="https://camel.apache.org/manual/transformer.html">Transformer</a> in the Camel user manual.
 *
 * @see   Transformer
 * @see   TransformerRegistry
 * @since 4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.TYPE })
@ServiceFactory("transformer")
public @interface DataTypeTransformer {

    /**
     * Data type transformer name. Identifies the data type transformer. It Should be unique in the Camel context. It
     * Can be a combination of scheme and name. It Is used to detect/reference the transformer when specifying
     * input/output data types on routes.
     */
    String name() default "";

    /**
     * Data type representing the input of the transformation. Also used to detect the transformer.
     */
    String fromType() default "";

    /**
     * Data type representing the result of the transformation. Also used to detect the transformer.
     */
    String toType() default "";

    /**
     * A human-readable description of what this transformer can do.
     */
    String description() default "";

}
