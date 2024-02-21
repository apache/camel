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
package org.apache.camel.component.atom;

import org.apache.camel.spi.Metadata;

/**
 * Atom constants
 */
public final class AtomConstants {

    /**
     * Header key for the List of {@link com.apptasticsoftware.rssreader.Item} object is stored on the in message on the
     * exchange.
     */
    @Metadata(description = "When consuming the List<com.apptasticsoftware.rssreader.Item> object is set to this header.",
              javaType = "java.util.List")
    public static final String ATOM_FEED = "CamelAtomFeed";

    private AtomConstants() {
        // utility class
    }
}
