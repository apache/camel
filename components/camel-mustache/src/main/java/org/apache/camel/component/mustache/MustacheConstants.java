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
package org.apache.camel.component.mustache;

import org.apache.camel.spi.Metadata;

/**
 * Mustache component constants
 */
public final class MustacheConstants {

    @Metadata(description = "The URI of a template resource that the producer renders in place of the resource of"
                            + " resourceUri, in the same form as resourceUri. Only used when allowTemplateFromHeader is true."
                            + " The producer removes the header and renders the template with the mustache endpoint for that"
                            + " URI, which takes the options of the component, not those of this endpoint.",
              javaType = "String")
    public static final String MUSTACHE_RESOURCE_URI = "CamelMustacheResourceUri";

    @Metadata(description = "The text of a template that the producer renders in place of the resource of resourceUri."
                            + " Only used when allowTemplateFromHeader is true. The producer removes the header and compiles"
                            + " the text for each exchange, with the startDelimiter and endDelimiter of the endpoint.",
              javaType = "String")
    public static final String MUSTACHE_TEMPLATE = "CamelMustacheTemplate";

    /**
     * Mustache endpoint URI prefix
     */
    public static final String MUSTACHE_ENDPOINT_URI_PREFIX = "mustache:";

    private MustacheConstants() {
        // Utility class
    }

}
