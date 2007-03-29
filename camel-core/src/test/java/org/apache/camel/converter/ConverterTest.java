/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter;

import junit.framework.TestCase;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;

/**
 * @version $Revision$
 */
public class ConverterTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(ConverterTest.class);

    protected TypeConverter converter = new DefaultTypeConverter();
    
    public void testConvertStringAndBytes() throws Exception {
        byte[] array = converter.convertTo(byte[].class, "foo");
        assertNotNull(array);

        log.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("Converted to String", "foo", text);
    }

    public void testConvertStringAndStreams() throws Exception {
        InputStream inputStream = converter.convertTo(InputStream.class, "bar");
        assertNotNull(inputStream);

        String text = converter.convertTo(String.class, inputStream);
        assertEquals("Converted to String", "bar", text);
    }
}
