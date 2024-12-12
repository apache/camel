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
 * Allows to plugin custom pre-processors that are processed before the DSL has loaded the source and compiled into a
 * Java object.
 * <p/>
 * This is used among others to detect imported classes that may need to be downloaded into classloader
 * to allow to compile the class.
 *
 * @see CompilePostProcessor
 */
public interface CompilePreProcessor {

    /**
     * Invoked before the class has been compiled
     *
     * @param  camelContext the camel context
     * @param  name         the name of the resource/class
     * @param  code         the source code of the class
     * @throws Exception    is thrown if error during post-processing
     */
    void preCompile(CamelContext camelContext, String name, String code) throws Exception;

}
