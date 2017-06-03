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
package org.apache.camel.component.restlet;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.util.IOHelper;

public final class RestletHelper {

    private RestletHelper() {
    }

    /**
     * Reads the response body from the given input stream.
     *
     * @param is       the input stream
     * @param exchange the exchange
     * @return the response body, can be <tt>null</tt> if no body
     * @throws java.io.IOException is thrown if error reading response body
     */
    public static Object readResponseBodyFromInputStream(InputStream is, Exchange exchange) throws IOException {
        if (is == null) {
            return null;
        }

        // convert the input stream to StreamCache if the stream cache is not disabled
        if (exchange.getProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.FALSE, Boolean.class)) {
            return is;
        } else {
            CachedOutputStream cos = new CachedOutputStream(exchange);
            IOHelper.copyAndCloseInput(is, cos);
            return cos.newStreamCache();
        }
    }

}
