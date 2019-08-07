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
package org.apache.camel.support;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

public class InputStreamIteratorTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testInputStreamIterator() throws Exception {
        context.start();

        Iterator it = Stream.of("ABC", "DEF", "1234567890").iterator();
        InputStreamIterator is = new InputStreamIterator(context.getTypeConverter(), it);
        // from the first chunk
        assertEquals(3, is.available());

        // copy input to output
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copy(is, bos);
        IOHelper.close(is, bos);

        // and we have the data back
        assertEquals("ABCDEF1234567890", bos.toString());
    }
}
