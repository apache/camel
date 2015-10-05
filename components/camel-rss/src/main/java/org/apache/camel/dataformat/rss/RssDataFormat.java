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
package org.apache.camel.dataformat.rss;

import java.io.InputStream;
import java.io.OutputStream;

import com.sun.syndication.feed.synd.SyndFeed;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;

/**
 * RSS DataFormat
 * <p/>
 * This data format supports two operations:
 * <ul>
 *   <li>marshal = from ROME SyndFeed to XML String </li>
 *   <li>unmarshal = from XML String to ROME SyndFeed </li>
 * </ul>
 * <p/>
 * Uses <a href="https://rome.dev.java.net/">ROME</a> for RSS parsing.
 * <p/>
 */
public class RssDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    @Override
    public String getDataFormatName() {
        return "rss";
    }

    public void marshal(Exchange exchange, Object body, OutputStream out) throws Exception {
        SyndFeed feed = ExchangeHelper.convertToMandatoryType(exchange, SyndFeed.class, body);
        String xml = RssConverter.feedToXml(feed);
        out.write(xml.getBytes());
    }

    public Object unmarshal(Exchange exchange, InputStream in) throws Exception {
        String xml = ExchangeHelper.convertToMandatoryType(exchange, String.class, in);
        return RssConverter.xmlToFeed(xml);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
