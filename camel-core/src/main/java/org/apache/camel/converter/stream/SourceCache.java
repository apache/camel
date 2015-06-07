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
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.StringSource;
import org.apache.camel.util.IOHelper;

/**
 * {@link org.apache.camel.StreamCache} implementation for {@link org.apache.camel.StringSource}s
 */
public final class SourceCache extends StringSource implements StreamCache {

    private static final long serialVersionUID = 1L;
    private final int length;

    public SourceCache(String data) {
        super(data);
        this.length = data.length();
    }

    public void reset() {
        // do nothing here
    }

    public void writeTo(OutputStream os) throws IOException {
        IOHelper.copy(getInputStream(), os);
    }

    public StreamCache copy(Exchange exchange) throws IOException {
        return new SourceCache(getText());
    }

    public boolean inMemory() {
        return true;
    }

    public long length() {
        return length;
    }
}