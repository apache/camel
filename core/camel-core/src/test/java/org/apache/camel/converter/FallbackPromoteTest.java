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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.TypeConverter;
import org.junit.Test;

public class FallbackPromoteTest extends ContextTestSupport {

    @Override
    protected boolean isLoadTypeConverters() {
        return true;
    }

    @Test
    public void testFallbackPromote() throws Exception {
        MyCoolBean cool = new MyCoolBean();
        cool.setCool("Camel rocks");

        TypeConverter tc = context.getTypeConverterRegistry().lookup(String.class, MyCoolBean.class);
        assertNull("No regular type converters", tc);

        String s = context.getTypeConverter().convertTo(String.class, cool);
        assertEquals("This is cool: Camel rocks", s);

        cool.setCool("It works");
        s = context.getTypeConverter().convertTo(String.class, cool);
        assertEquals("This is cool: It works", s);

        tc = context.getTypeConverterRegistry().lookup(String.class, MyCoolBean.class);
        assertNotNull("Should have been promoted", tc);
    }

}
