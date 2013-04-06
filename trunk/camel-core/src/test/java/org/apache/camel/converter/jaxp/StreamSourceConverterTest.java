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
package org.apache.camel.converter.jaxp;

import java.io.InputStream;
import java.io.Reader;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;

/**
 * @version 
 */
public class StreamSourceConverterTest extends ContextTestSupport {

    public void testToInputStream() throws Exception {
        StreamSource source = context.getTypeConverter().convertTo(StreamSource.class, "<foo>bar</foo>");

        InputStream out = StreamSourceConverter.toInputStream(source);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToReader() throws Exception {
        StreamSource source = context.getTypeConverter().convertTo(StreamSource.class, "<foo>bar</foo>");

        Reader out = StreamSourceConverter.toReader(source);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }
}
