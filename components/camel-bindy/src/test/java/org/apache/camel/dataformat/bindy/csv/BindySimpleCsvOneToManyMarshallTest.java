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
package org.apache.camel.dataformat.bindy.csv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.simple.onetomany.Author;
import org.apache.camel.dataformat.bindy.model.simple.onetomany.Book;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleCsvOneToManyMarshallTest extends AbstractJUnit4SpringContextTests {

    private List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
    private String result = "Charles,Moulliard,Camel in Action 1,2010,43\r\n" + "Charles,Moulliard,Camel in Action 2,2012,43\r\n"
                            + "Charles,Moulliard,Camel in Action 3,2013,43\r\n" + "Charles,Moulliard,Camel in Action 4,,43\r\n";

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testMarshallMessage() throws Exception {
        resultEndpoint.expectedBodiesReceived(result);

        template.sendBody(generateModel());

        resultEndpoint.assertIsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
        Author author;
        Book book;

        Map<String, Object> model = new HashMap<String, Object>();
        List<Book> books = new ArrayList<Book>();
        // List<Reference> references = new ArrayList<Reference>();
        // List<Editor> editors = new ArrayList<Editor>();

        author = new Author();
        author.setFirstName("Charles");
        author.setLastName("Moulliard");
        author.setAge("43");

        // 1st Book
        book = new Book();
        book.setTitle("Camel in Action 1");
        book.setYear("2010");

        books.add(book);

        // 2nd book
        book = new Book();
        book.setTitle("Camel in Action 2");
        book.setYear("2012");

        books.add(book);

        // 3rd book
        book = new Book();
        book.setTitle("Camel in Action 3");
        book.setYear("2013");
        books.add(book);

        // 4th book
        book = new Book();
        book.setTitle("Camel in Action 4");
        book.setYear(null);
        books.add(book);

        // Add books to author
        author.setBooks(books);

        model.put(author.getClass().getName(), author);

        models.add(model);

        return models;
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.onetomany.Author.class);

        public void configure() {
            from("direct:start").marshal(camelDataFormat).to("mock:result");
        }

    }

}
