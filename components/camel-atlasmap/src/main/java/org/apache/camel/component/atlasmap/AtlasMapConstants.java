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
package org.apache.camel.component.atlasmap;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * AtlasMap Constants.
 */
public final class AtlasMapConstants {

    @Metadata(description = "The new resource URI to use.", javaType = "java.lang.String")
    public static final String ATLAS_RESOURCE_URI = "CamelAtlasResourceUri";
    @Metadata(description = "The Atlas mapping to use.", javaType = "java.lang.String")
    public static final String ATLAS_MAPPING = "CamelAtlasMapping";
    public static final String ATLAS_SOURCE_MAP = "CamelAtlasSourceMap";
    public static final String ATLAS_TARGET_MAP = "CamelAtlasTargetMap";
    @Metadata(description = "The content type that is set according to the datasource (json or xml).",
              javaType = "java.lang.String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;

    private AtlasMapConstants() {
    }

}
