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
package org.apache.camel.spi;

import org.apache.camel.Message;
import org.apache.camel.StaticService;

/**
 * Strategy for computing the size of message payloads (body and headers) for observation purposes.
 * <p/>
 * Not all message body types can be sized efficiently. For example, Java objects have no inherent "size", and streaming
 * payloads would be destructive to read unless stream caching is enabled. Implementations should return -1 when the
 * size cannot be determined without side effects.
 *
 * @since 4.21
 */
public interface MessageSizeStrategy extends StaticService {

    /**
     * Whether message size computation is enabled.
     */
    void setEnabled(boolean enabled);

    /**
     * Whether message size computation is enabled.
     */
    boolean isEnabled();

    /**
     * Computes the size of the message body in bytes.
     *
     * @param  message the message
     * @return         the body size in bytes, 0 if the body is null, or -1 if the size cannot be determined
     */
    long computeBodySize(Message message);

    /**
     * Computes the total size of all message headers in bytes (keys and values).
     *
     * @param  message the message
     * @return         the total headers size in bytes, or -1 if the size cannot be determined
     */
    long computeHeadersSize(Message message);
}
