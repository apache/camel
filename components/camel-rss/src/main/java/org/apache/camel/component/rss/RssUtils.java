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
package org.apache.camel.component.rss;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import org.apache.commons.codec.binary.Base64;

public final class RssUtils {

    private RssUtils() {
        // Helper class
    }

    public static SyndFeed createFeed(String feedUri) throws Exception {
        return createFeed(feedUri, Thread.currentThread().getContextClassLoader());
    }

    public static SyndFeed createFeed(String feedUri, ClassLoader classLoader) throws Exception {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            InputStream in = new URL(feedUri).openStream();
            SyndFeedInput input = new SyndFeedInput();
            return input.build(new XmlReader(in));
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    public static SyndFeed createFeed(String feedUri, String username, String password) throws Exception {
        return createFeed(feedUri, username, password, Thread.currentThread().getContextClassLoader());
    }

    public static SyndFeed createFeed(String feedUri, String username, String password, ClassLoader classLoader) throws Exception {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            URL feedUrl = new URL(feedUri);
            HttpURLConnection httpcon = (HttpURLConnection) feedUrl.openConnection();
            String encoding = Base64.encodeBase64String(username.concat(":").concat(password).getBytes());
            httpcon.setRequestProperty("Authorization", "Basic " + encoding);
            SyndFeedInput input = new SyndFeedInput();
            return input.build(new XmlReader(httpcon));
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }
}
