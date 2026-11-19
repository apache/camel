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
package org.apache.camel.component.langchain4j.service;

import org.apache.camel.spi.Metadata;

/**
 * Header constants for the LangChain4j Service component.
 * <p>
 * Template variables bind from the {@code CamelLangChain4jServiceVariables} header, a {@code Map<String, Object>} keyed
 * by template variable name. For example, {@code @V("context")} binds from the {@code "context"} key.
 */
public final class LangChain4jServiceHeaders {

    // The docs generator drops an '@', so name the annotation in words rather than writing it out
    @Metadata(description = "Map of template variable name to value, bound to the template variable parameters of the"
                            + " AI Service method",
              javaType = "java.util.Map")
    public static final String VARIABLES = "CamelLangChain4jServiceVariables";

    @Metadata(description = "Memory ID for stateful AI Services", javaType = "Object")
    public static final String MEMORY_ID = "CamelLangChain4jServiceMemoryId";

    private LangChain4jServiceHeaders() {
    }
}
