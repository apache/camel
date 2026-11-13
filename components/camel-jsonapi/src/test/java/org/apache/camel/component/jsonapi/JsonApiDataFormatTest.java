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
package org.apache.camel.component.jsonapi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.github.jasminb.jsonapi.exceptions.UnregisteredTypeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonApiDataFormatTest extends CamelTestSupport {

    @Test
    public void testJsonApiMarshal() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        MyBook book = this.generateTestDataAsObject();

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, book, baos);

        String jsonApiOutput = baos.toString();
        assertNotNull(jsonApiOutput);
        assertEquals(this.generateTestDataAsString(), jsonApiOutput);
    }

    @Test
    public void testJsonApiMarshalNoAnnotationOnType() {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThrows(DocumentSerializationException.class,
                () -> jsonApiDataFormat.marshal(exchange, new FooBar(), baos));
    }

    @Test
    public void testJsonApiMarshalWrongType() {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThrows(DocumentSerializationException.class,
                () -> jsonApiDataFormat.marshal(exchange, new MyFooBar("bar"), baos));
    }

    @Test
    public void testJsonApiUnmarshall() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(MyBook.class, formats);

        String jsonApiInput = this.generateTestDataAsString();

        Exchange exchange = new DefaultExchange(context);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange, new ByteArrayInputStream(jsonApiInput.getBytes()));

        assertNotNull(outputObj);
        MyBook book = (MyBook) outputObj;
        assertEquals("Camel in Action", book.getTitle());
        assertEquals("1", book.getAuthor().getAuthorId());
    }

    @Test
    public void testJsonApiUnmarshalWrongType() {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypeClasses(formats);
        jsonApiDataFormat.setMainFormatTypeClass(MyBook.class);

        String jsonApiInput = "{\"data\":{\"type\":\"animal\",\"id\":\"camel\",\"attributes\":{\"humps\":\"2\"}}}";

        Exchange exchange = new DefaultExchange(context);
        final ByteArrayInputStream stream = new ByteArrayInputStream(jsonApiInput.getBytes());

        assertThrows(UnregisteredTypeException.class,
                () -> jsonApiDataFormat.unmarshal(exchange, stream));
    }

    @Test
    public void testJsonApiUnmarshalCollection() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(MyBook.class, formats);

        Exchange exchange = new DefaultExchange(context);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange,
                new ByteArrayInputStream(generateBooksAsString().getBytes()));

        List<?> books = assertInstanceOf(List.class, outputObj);
        assertEquals(2, books.size());
        MyBook first = assertInstanceOf(MyBook.class, books.get(0));
        assertEquals("1617292931", first.getIsbn());
        assertEquals("Camel in Action", first.getTitle());
        MyBook second = assertInstanceOf(MyBook.class, books.get(1));
        assertEquals("1935182366", second.getIsbn());
        assertEquals("Camel in Action, first edition", second.getTitle());
    }

    @Test
    public void testJsonApiUnmarshalCollectionDataNotFirst() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(MyBook.class, formats);

        // the data member comes after other members, one of them an object with a data member of its own
        String jsonApiInput = "{\"meta\":{\"data\":{\"total\":1}},\"links\":{\"self\":\"/books\"},"
                              + "\"data\":[{\"type\":\"book\",\"id\":\"1617292931\","
                              + "\"attributes\":{\"title\":\"Camel in Action\"}}]}";

        Exchange exchange = new DefaultExchange(context);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange, jsonApiInput.getBytes());

        List<?> books = assertInstanceOf(List.class, outputObj);
        assertEquals(1, books.size());
        assertEquals("Camel in Action", assertInstanceOf(MyBook.class, books.get(0)).getTitle());
    }

    @Test
    public void testJsonApiUnmarshalEmptyCollection() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(MyBook.class, formats);

        Exchange exchange = new DefaultExchange(context);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange, "{\"data\":[]}".getBytes());

        List<?> books = assertInstanceOf(List.class, outputObj);
        assertTrue(books.isEmpty());
    }

    @Test
    public void testJsonApiMarshalCollection() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, generateBooksAsObjects(), baos);

        assertEquals(generateBooksAsString(), baos.toString());
    }

    @Test
    public void testJsonApiMarshalArray() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, generateBooksAsObjects().toArray(new MyBook[0]), baos);

        assertEquals(generateBooksAsString(), baos.toString());
    }

    @Test
    public void testJsonApiMarshalEmptyCollection() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, Collections.emptyList(), baos);

        assertEquals("{\"data\":[]}", baos.toString());
    }

    @Test
    public void testJsonApiCollectionRoundTrip() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(MyBook.class, formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, generateBooksAsObjects(), baos);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange, baos.toByteArray());

        List<?> books = assertInstanceOf(List.class, outputObj);
        assertEquals(2, books.size());
        MyBook first = assertInstanceOf(MyBook.class, books.get(0));
        assertEquals("Camel in Action", first.getTitle());
        assertEquals("1", first.getAuthor().getAuthorId());
        assertEquals("Camel in Action, first edition", assertInstanceOf(MyBook.class, books.get(1)).getTitle());
    }

    @Test
    public void testCamelJsonApiMarshal() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultMarshal");
        mock.expectedMessageCount(1);

        MyAuthor author = new MyAuthor();
        author.setAuthorId("1");
        author.setFirstName("Claus");
        author.setLastName("Ibsen");

        Exchange exchange = this.createExchangeWithBody(author);
        Exchange outExchange = template.send("direct:startMarshal", exchange);
        String outBody = outExchange.getIn().getBody(String.class);
        assertNotNull(outBody);
        assertEquals(this.generateAuthorString(), outBody);

        MockEndpoint.assertIsSatisfied(context);

    }

    @Test
    public void testCamelJsonApiUnmarshal() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultUnmarshal");
        mock.expectedMessageCount(1);

        Exchange exchange = this.createExchangeWithBody(this.generateAuthorString());
        Exchange outExchange = template.send("direct:startUnmarshal", exchange);

        MockEndpoint.assertIsSatisfied(context);

        MyAuthor author = outExchange.getIn().getBody(MyAuthor.class);
        assertNotNull(author);
        assertEquals("1", author.getAuthorId());
        assertEquals("Claus", author.getFirstName());
        assertEquals("Ibsen", author.getLastName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Class<?>[] formats = { MyAuthor.class };
                JsonApiDataFormat jsonApi = new JsonApiDataFormat(MyAuthor.class, formats);

                from("direct:startMarshal").marshal(jsonApi).to("mock:resultMarshal");

                from("direct:startUnmarshal").unmarshal(jsonApi).to("mock:resultUnmarshal");
            }
        };
    }

    private String generateAuthorString() {
        return "{\"data\":{\"type\":\"author\",\"id\":\"1\",\"attributes\":{\"firstName\":\"Claus\",\"lastName\":\"Ibsen\"}}}";
    }

    private String generateTestDataAsString() {
        return "{\"data\":{\"type\":\"book\",\"id\":\"1617292931\",\"attributes\":{\"title\":\"Camel in Action\"},\"relationships\":{\"author\":{\"data\":{\"type\":\"author\",\"id\":\"1\"}}}}}";
    }

    private String generateBooksAsString() {
        return "{\"data\":[{\"type\":\"book\",\"id\":\"1617292931\",\"attributes\":{\"title\":\"Camel in Action\"},"
               + "\"relationships\":{\"author\":{\"data\":{\"type\":\"author\",\"id\":\"1\"}}}},"
               + "{\"type\":\"book\",\"id\":\"1935182366\",\"attributes\":{\"title\":\"Camel in Action, first edition\"},"
               + "\"relationships\":{\"author\":{\"data\":{\"type\":\"author\",\"id\":\"1\"}}}}]}";
    }

    private List<MyBook> generateBooksAsObjects() {
        MyBook second = new MyBook();
        second.setIsbn("1935182366");
        second.setTitle("Camel in Action, first edition");
        second.setAuthor(generateTestDataAsObject().getAuthor());
        return List.of(generateTestDataAsObject(), second);
    }

    private MyBook generateTestDataAsObject() {
        MyAuthor author = new MyAuthor();
        author.setAuthorId("1");
        author.setFirstName("Claus");
        author.setLastName("Ibsen");

        MyBook book = new MyBook();
        book.setIsbn("1617292931");
        book.setTitle("Camel in Action");
        book.setAuthor(author);

        return book;
    }

}
