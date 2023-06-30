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
package org.apache.camel.catalog.maven;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.ivy.util.url.BasicURLHandler;
import org.apache.ivy.util.url.HttpClientHandler;

/**
 * A {@link HttpClientHandler} which uses HttpClient for downloading via http/https and have support for connection
 * timeouts which otherwise is not supported by default in Apache Ivy.
 */
public class TimeoutHttpClientHandler extends HttpClientHandler {

    // use basic handler for non http/https as it can load from jar/file etc
    private BasicURLHandler basic = new BasicURLHandler();

    private int timeout = 10000;

    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout in millis (http.socket.timeout) when downloading via http/https protocols.
     * <p/>
     * The default value is 10000
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public URLInfo getURLInfo(URL url) {
        // ensure we always use a timeout
        String protocol = url.getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            return super.getURLInfo(url, timeout);
        } else {
            // use basic for non http
            return basic.getURLInfo(url, timeout);
        }
    }

    @Override
    public InputStream openStream(URL url) throws IOException {
        String protocol = url.getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            return super.openStream(url);
        } else {
            // use basic for non http
            return basic.openStream(url);
        }
    }
}
