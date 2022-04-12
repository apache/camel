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
package org.apache.camel.jaxb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.example.Bar;
import org.apache.camel.example.Foo;
import org.apache.camel.foo.bar.PersonType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CamelJaxbFallbackConverterTest extends ExchangeTestSupport {

    @Test
    public void testFallbackConverterWithoutObjectFactory() {
        TypeConverter converter = context.getTypeConverter();
        Foo foo = converter.convertTo(Foo.class, exchange,
                "<foo><zot name=\"bar1\" value=\"value\" otherValue=\"otherValue\"/></foo>");
        assertNotNull(foo, "foo should not be null");
        assertEquals("value", foo.getBarRefs().get(0).getValue());

        foo.getBarRefs().clear();
        Bar bar = new Bar();
        bar.setName("myName");
        bar.setValue("myValue");
        foo.getBarRefs().add(bar);

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

        String value = converter.convertTo(String.class, exchange, foo);

        assertTrue(value.indexOf("<bar name=\"myName\" value=\"myValue\"/>") > 0, "Should get a right marshalled string");
    }

    @Test
    public void testFallbackConverterUnmarshalWithNonJAXBComplaintValue() {
        TypeConverter converter = context.getTypeConverter();

        try {
            converter.convertTo(Foo.class, "Not every String is XML");
            fail("Should have thrown exception");
        } catch (TypeConversionException e) {
            // expected
        }

        try {
            converter.convertTo(Bar.class, "<bar></bar");
            fail("Should have thrown exception");
        } catch (TypeConversionException e) {
            // expected
        }
    }

    @Test
    public void testConverter() throws Exception {
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, exchange,
                "<Person><firstName>FOO</firstName><lastName>BAR</lastName></Person>");
        assertNotNull(person, "Person should not be null");
        assertEquals("FOO", person.getFirstName(), "Get the wrong first name");
        assertEquals("BAR", person.getLastName(), "Get the wrong second name");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

        String value = converter.convertTo(String.class, exchange, person);
        assertTrue(value.indexOf("<lastName>BAR</lastName>") > 0, "Should get a right marshalled string");

        byte[] buffers = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>".getBytes("UTF-8");
        InputStream is = new ByteArrayInputStream(buffers);
        try {
            converter.convertTo(PersonType.class, exchange, is);
            fail("Should have thrown exception");
        } catch (TypeConversionException e) {
            // expected
        }
    }

    @Test
    public void testFilteringConverter() throws Exception {
        byte[] buffers = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>".getBytes("UTF-8");
        InputStream is = new ByteArrayInputStream(buffers);
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
        exchange.setProperty(Exchange.FILTER_NON_XML_CHARS, true);
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, exchange, is);
        assertNotNull(person, "Person should not be null ");
        assertEquals("FOO", person.getFirstName(), "Get the wrong first name");
        assertEquals("BAR ", person.getLastName(), "Get the wrong second name");

        person.setLastName("BAR\u0008\uD8FF");
        String value = converter.convertTo(String.class, exchange, person);
        assertTrue(value.indexOf("<lastName>BAR  </lastName>") > 0, "Didn't filter the non-xml chars");

        exchange.setProperty(Exchange.FILTER_NON_XML_CHARS, false);

        value = converter.convertTo(String.class, exchange, person);
        assertTrue(value.indexOf("<lastName>BAR\uD8FF</lastName>") > 0, "Should not filter the non-xml chars");
    }
}
