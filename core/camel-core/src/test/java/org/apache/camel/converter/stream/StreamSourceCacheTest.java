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
package org.apache.camel.converter.stream;

import java.io.ByteArrayOutputStream;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.xml.StreamSourceCache;
import org.junit.Test;

public class StreamSourceCacheTest extends ContextTestSupport {

    @Test
    public void testStreamSourceCache() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        StreamSource source = context.getTypeConverter().convertTo(StreamSource.class, "<foo>bar</foo>");
        StreamSourceCache cache = new StreamSourceCache(source, exchange);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cache.writeTo(bos);

        String s = context.getTypeConverter().convertTo(String.class, bos);
        assertEquals("<foo>bar</foo>", s);
    }

}
