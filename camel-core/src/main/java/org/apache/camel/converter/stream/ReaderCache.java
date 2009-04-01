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
package org.apache.camel.converter.stream;

import java.io.IOException;
import java.io.StringReader;

import org.apache.camel.StreamCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link org.apache.camel.StreamCache} implementation for Cache the Reader {@link java.io.Reader}s
 */
public class ReaderCache extends StringReader implements StreamCache {

    private static final transient Log LOG = LogFactory.getLog(ReaderCache.class);

    private String data;

    public ReaderCache(String data) {
        super(data);
        this.data = data;
    }

    public void close() {
        // Do not release the string for caching
    }

    @Override
    public void reset() {
        try {
            super.reset();
        } catch (IOException e) {
            LOG.warn("Cannot reset cache", e);
        }
    }

    String getData() {
        return data;
    }

}
