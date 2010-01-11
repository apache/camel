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
package org.apache.camel.jaxb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.foo.bar.PersonType;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CamelJaxbFallbackConverterTest extends CamelTestSupport {
    
    @Test
    public void testConvertor() throws Exception {
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, 
            "<Person><firstName>FOO</firstName><lastName>BAR</lastName></Person>");
        assertNotNull("Person should not be null ", person);
        assertEquals("Get the wrong first name ", person.getFirstName(), "FOO");
        assertEquals("Get the wrong second name ", person.getLastName(), "BAR");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
       
        String value = converter.convertTo(String.class, exchange, person);
        assertTrue("Didn't filter the non-xml chars", value.indexOf("<lastName>BAR</lastName>") > 0);
    }
    
    @Test
    public void testFilteringConvertor() throws Exception {
        final byte[] buffers = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>".getBytes("UTF-8");
        InputStream is = new ByteArrayInputStream(buffers);
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
        exchange.setProperty(Exchange.FILTER_NON_XML_CHARS, true);
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, exchange, is);
        assertNotNull("Person should not be null ", person);
        assertEquals("Get the wrong first name ", person.getFirstName(), "FOO");
        assertEquals("Get the wrong second name ", person.getLastName(), "BAR ");
        
        person.setLastName("BAR\u0008\uD8FF");
        String value = converter.convertTo(String.class, exchange, person);
        assertTrue("Didn't filter the non-xml chars", value.indexOf("<lastName>BAR  </lastName>") > 0);
        
        exchange.setProperty(Exchange.FILTER_NON_XML_CHARS, false);
        value = converter.convertTo(String.class, exchange, person);
        assertTrue("Didn't filter the non-xml chars", value.indexOf("<lastName>BAR\uD8FF</lastName>") > 0);
    }

}
