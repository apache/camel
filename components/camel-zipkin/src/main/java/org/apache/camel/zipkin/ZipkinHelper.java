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
package org.apache.camel.zipkin;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;

/**
 * Helper class.
 */
public final class ZipkinHelper {

    private ZipkinHelper() {
    }

    public static StreamCache prepareBodyForLogging(Exchange exchange, boolean streams) {
        if (!streams) {
            // no need to prepare if streams is not enabled
            return null;
        }

        Message message = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        // check if body is already cached
        Object body = message.getBody();
        if (body == null) {
            return null;
        } else if (body instanceof StreamCache) {
            StreamCache sc = (StreamCache) body;
            // reset so the cache is ready to be used before processing
            sc.reset();
            return sc;
        }
        // cache the body and if we could do that replace it as the new body
        StreamCache sc = exchange.getContext().getStreamCachingStrategy().cache(exchange);
        if (sc != null) {
            message.setBody(sc);
        }
        return sc;
    }

}
