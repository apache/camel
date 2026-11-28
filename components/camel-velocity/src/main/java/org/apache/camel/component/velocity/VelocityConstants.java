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
package org.apache.camel.component.velocity;

import org.apache.camel.spi.Metadata;

/**
 * Velocity Constants.
 */
public final class VelocityConstants {

    @Metadata(description = "The URI of a template that the producer renders in place of the template of resourceUri, in the"
                            + " same form as resourceUri. Only used when allowTemplateFromHeader is true. The producer removes"
                            + " the header and hands the exchange to the velocity endpoint of that URI, with the same options as"
                            + " this endpoint, which renders the text of a CamelVelocityTemplate header instead if one is set.",
              javaType = "String")
    public static final String VELOCITY_RESOURCE_URI = "CamelVelocityResourceUri";
    @Metadata(description = "The text of a template that the producer renders in place of the template of resourceUri. Only"
                            + " used when allowTemplateFromHeader is true. The producer removes the header after reading it.",
              javaType = "String")
    public static final String VELOCITY_TEMPLATE = "CamelVelocityTemplate";
    @Metadata(description = "The org.apache.velocity.context.Context that the template reads its data from, in place of the"
                            + " context that the producer builds from the exchange with the body, the headers and the other"
                            + " entries. Only used when allowTemplateFromHeader is true. The CamelVelocitySupplementalContext"
                            + " header is then ignored.",
              javaType = "org.apache.velocity.context.Context")
    public static final String VELOCITY_CONTEXT = "CamelVelocityContext";
    @Metadata(description = "A Map of entries that the producer adds to the context that it builds from the exchange, where an"
                            + " entry replaces the one with the same key, such as body or headers. Only used when"
                            + " allowTemplateFromHeader is true, and not when the CamelVelocityContext header is set.",
              javaType = "Map<String, Object>")
    public static final String VELOCITY_SUPPLEMENTAL_CONTEXT = "CamelVelocitySupplementalContext";

    private VelocityConstants() {
        // Utility class
    }
}
