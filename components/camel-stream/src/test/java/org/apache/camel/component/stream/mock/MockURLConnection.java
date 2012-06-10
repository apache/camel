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
package org.apache.camel.component.stream.mock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class MockURLConnection extends URLConnection {

    private static final ThreadLocal<OutputStream> THREAD_OUTPUT_STREAM = new ThreadLocal<OutputStream>();

    public MockURLConnection(URL url) {
        super(url);
    }

    public static void setOutputStream(OutputStream outputStream) {
        THREAD_OUTPUT_STREAM.set(outputStream);
    }

    @Override
    public void connect() throws IOException {
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return THREAD_OUTPUT_STREAM.get();
    }

}
