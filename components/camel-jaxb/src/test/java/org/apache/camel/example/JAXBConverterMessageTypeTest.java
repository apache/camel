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
package org.apache.camel.example;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.jaxb.MessageType;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * Unit test for JABX conversion of MessageType
 */
public class JAXBConverterMessageTypeTest extends TestCase {
    protected CamelContext context = new DefaultCamelContext();
    protected TypeConverter converter = context.getTypeConverter();

    public void testConverter() throws Exception {
        MessageType message = converter.convertTo(MessageType.class, "<message><hello>bar</hello></message>");
        assertNotNull("Message should not be null!", message);
    }

}
