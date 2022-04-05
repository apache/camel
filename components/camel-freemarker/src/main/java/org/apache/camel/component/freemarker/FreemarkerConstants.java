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
package org.apache.camel.component.freemarker;

import org.apache.camel.spi.Metadata;

/**
 * Freemarker Constants
 */
public final class FreemarkerConstants {

    @Metadata(description = "A URI for the template resource to use instead of the endpoint\n" +
                            "configured.",
              javaType = "String")
    public static final String FREEMARKER_RESOURCE_URI = "CamelFreemarkerResourceUri";
    @Metadata(description = "The template to use instead of the endpoint configured.", javaType = "String")
    public static final String FREEMARKER_TEMPLATE = "CamelFreemarkerTemplate";
    @Metadata(description = "The data model", javaType = "Object")
    public static final String FREEMARKER_DATA_MODEL = "CamelFreemarkerDataModel";

    private FreemarkerConstants() {
        // Utility class
    }
}
