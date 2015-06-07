/**
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
package org.apache.camel.component.chunk;

/**
 * Chunk component constants
 */
public final class ChunkConstants {

    /**
     * Header containing a Chunk template location
     */
    public static final String CHUNK_RESOURCE_URI = "ChunkResourceUri";

    /**
     * Header containing the Chunk template code
     */
    public static final String CHUNK_TEMPLATE = "ChunkTemplate";

    /**
     * Chunk endpoint URI prefix
     */
    public static final String CHUNK_ENDPOINT_URI_PREFIX = "chunk:";
    
    /**
     * Chunk Template extension
     */
    public static final String CHUNK_LAYER_SEPARATOR = "#";

    private ChunkConstants() {
        // Utility class
    }

}
