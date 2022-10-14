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
package org.apache.camel.jsonpath;

import java.io.File;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SpringJsonPathLanguageTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/jsonpath/SpringJsonPathLanguageTest.xml");
    }

    @Test
    public void testExpensiveBooks() throws Exception {
        getMockEndpoint("mock:books").expectedMessageCount(1);

        template.sendBody("direct:start", new File("src/test/resources/expensive.json"));

        MockEndpoint.assertIsSatisfied(context);

        String books = getMockEndpoint("mock:books").getReceivedExchanges().get(0).getIn().getBody(String.class);
        // convert the result to valid Json string
        books = books.replaceAll("\\{", "{\"").replaceAll("=", "\": \"").replaceAll(", ", "\", \"").replaceAll("}", "\"}");

        ObjectMapper objectMapper = new ObjectMapper();
        Book[] result = objectMapper.readValue(books, Book[].class);
        Book[] expected = new Book[] { new Book("programming", "Claus Ibsen,Jonathan Anstey", "Camel in Action", "39.99", "978-193-518-236-8") };
        assertArrayEquals(expected, result);
    }



    public static class Book {

        @JsonProperty(value = "category")
        String category;

        @JsonProperty(value = "author")
        String author;

        @JsonProperty(value = "title")
        String title;

        @JsonProperty(value = "price")
        String price;

        @JsonProperty(value = "isbn")
        String isbn;


        public Book() {

        }

        public Book(String category, String author, String title, String price, String isbn) {
            this.category = category;
            this.author = author;
            this.title = title;
            this.price = price;
            this.isbn = isbn;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Book) {
                Book book = (Book) other;
                return Objects.equals(category, book.category)
                        && Objects.equals(author, book.author)
                        && Objects.equals(title, book.title)
                        && Objects.equals(price, book.price)
                        && Objects.equals(isbn, book.isbn);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, author, title, price, isbn);
        }

    }

}