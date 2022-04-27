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

import org.apache.camel.CamelContext;

/**
 * Allows to plugin custom post processors that are processed after the DSL has loaded the source and compiled into a
 * Java object.
 * <p/>
 * This is used to detect and handle {@link org.apache.camel.BindToRegistry} and {@link org.apache.camel.Converter}
 * classes.
 */
public interface CompilePostProcessor {

    /**
     * Invoked after the class has been compiled
     *
     * @param  camelContext the camel context
     * @param  name         the name of the resource/class
     * @param  clazz        the class
     * @param  byteCode     byte code that was compiled from the source as the class (only supported on some DSLs)
     * @param  instance     the object created as instance of the class (if any)
     * @throws Exception    is thrown if error during post-processing
     */
    void postCompile(
            CamelContext camelContext, String name,
            Class<?> clazz, byte[] byteCode, Object instance)
            throws Exception;

}
