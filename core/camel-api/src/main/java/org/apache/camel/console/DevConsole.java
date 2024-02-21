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
package org.apache.camel.console;

import java.util.Collections;
import java.util.Map;

/**
 * Developer Console
 */
public interface DevConsole {

    String CONSOLE_ID = "console.id";
    String CONSOLE_GROUP = "console.group";

    enum MediaType {
        TEXT,
        JSON
    }

    /**
     * The group of this console.
     */
    String getGroup();

    /**
     * The ID of this console.
     */
    String getId();

    /**
     * Display name of this console.
     */
    String getDisplayName();

    /**
     * Short description of this console.
     */
    String getDescription();

    /**
     * Whether this console supports the given media type.
     *
     * @param  mediaType the media type
     * @return           true if supported, false if not
     */
    boolean supportMediaType(MediaType mediaType);

    /**
     * Invokes and gets the output from this console.
     */
    default Object call(MediaType mediaType) {
        return call(mediaType, Collections.emptyMap());
    }

    /**
     * Invokes and gets the output from this console.
     *
     * The options argument can be used to pass information specific to the call. The implementation is responsible to
     * catch and handle any exception thrown by the underlying technology, including unchecked ones.
     */
    Object call(MediaType mediaType, Map<String, Object> options);

}
