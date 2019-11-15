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
package org.apache.camel.dataformat.rss;

import java.io.StringReader;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.SyndFeedOutput;
import org.apache.camel.Converter;

@Converter(generateLoader = true)
public final class RssConverter {
    private RssConverter() {
    }

    @Converter
    public static String feedToXml(SyndFeed feed) throws FeedException {
        SyndFeedOutput out = new SyndFeedOutput();
        return out.outputString(feed);
    }

    @Converter
    public static SyndFeed xmlToFeed(String xml) throws FeedException {
        SyndFeedInput input = new SyndFeedInput();
        return input.build(new StringReader(xml));
    }
}
