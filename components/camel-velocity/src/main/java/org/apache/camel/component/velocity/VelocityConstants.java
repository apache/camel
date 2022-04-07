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

    @Metadata(description = "The name of the velocity template.", javaType = "String")
    public static final String VELOCITY_RESOURCE_URI = "CamelVelocityResourceUri";
    @Metadata(description = "The content of the velocity template.", javaType = "String")
    public static final String VELOCITY_TEMPLATE = "CamelVelocityTemplate";
    @Metadata(description = "The velocity context to use.", javaType = "org.apache.velocity.context.Context")
    public static final String VELOCITY_CONTEXT = "CamelVelocityContext";
    @Metadata(description = "To add additional information to the used VelocityContext.\n" +
                            "The value of this header should be a `Map` with key/values that will\n" +
                            "added (override any existing key with the same name). \n" +
                            "This can be used to pre setup some common key/values you want to reuse\n" +
                            "in your velocity endpoints.",
              javaType = "Map<String, Object>")
    public static final String VELOCITY_SUPPLEMENTAL_CONTEXT = "CamelVelocitySupplementalContext";

    private VelocityConstants() {
        // Utility class
    }
}
