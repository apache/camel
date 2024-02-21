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
package org.apache.camel.component.thymeleaf;

import org.apache.camel.spi.Metadata;

public class ThymeleafConstants {

    @Metadata(description = "The name of the Thymeleaf template.", javaType = "String")
    public static final String THYMELEAF_RESOURCE_URI = "CamelThymeleafResourceUri";

    @Metadata(description = "The content of the Thymeleaf template.", javaType = "String")
    public static final String THYMELEAF_TEMPLATE = "CamelThymeleafTemplate";

    @Metadata(description = "The value of this header should be a `Map` with key/values that will be\n" +
                            "override any existing key with the same name. \n" +
                            "This can be used to preconfigure common key/values you want to reuse\n" +
                            "in your Thymeleaf endpoints.",
              javaType = "Map<String, Object>")
    public static final String THYMELEAF_VARIABLE_MAP = "CamelThymeleafVariableMap";

    @Metadata(description = "The ServletContext for a web application.", javaType = "jakarta.servlet.ServletContext")
    public static final String THYMELEAF_SERVLET_CONTEXT = "CamelThymeleafServletContext";

    private ThymeleafConstants() {

    }

}
