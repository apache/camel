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
package org.apache.camel.component.restlet.converter;

import org.junit.Test;
import org.restlet.data.MediaType;

import static org.junit.Assert.assertEquals;

public class RestletConverterTest {

    @Test
    public void shouldConvertMediaTypes() {
        final MediaType[] mediaTypes = RestletConverter.toMediaTypes("a/b, c/d");

        assertEquals("Expecting two parsed media types", 2, mediaTypes.length);
        assertEquals("Expecting first to be a/b", MediaType.valueOf("a/b"), mediaTypes[0]);
        assertEquals("Expecting second to be c/d", MediaType.valueOf("c/d"), mediaTypes[1]);
    }

    @Test
    public void shouldConvertNoMediaTypes() {
        final MediaType[] mediaTypes = RestletConverter.toMediaTypes("");

        assertEquals("Expecting no parsed media types", 0, mediaTypes.length);
    }
}
