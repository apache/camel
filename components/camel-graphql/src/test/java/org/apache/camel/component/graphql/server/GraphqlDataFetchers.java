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
package org.apache.camel.component.graphql.server;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import graphql.schema.DataFetcher;

public final class GraphqlDataFetchers {

    private static final List<Book> BOOKS = Arrays.asList(
            new Book("book-1", "Harry Potter and the Philosopher's Stone", "author-1"),
            new Book("book-2", "Moby Dick", "author-2"),
            new Book("book-3", "Interview with the vampire", "author-3"));

    private static final List<Author> AUTHORS = Arrays.asList(
            new Author("author-1", "Joanne Rowling"),
            new Author("author-2", "Herman Melville"),
            new Author("author-3", "Anne Rice"));

    private GraphqlDataFetchers() {
    }

    public static DataFetcher<List<Book>> getBooksDataFetcher() {
        return dataFetchingEnvironment -> BOOKS;
    }

    public static DataFetcher<Book> getBookByIdDataFetcher() {
        return dataFetchingEnvironment -> {
            String bookId = dataFetchingEnvironment.getArgument("id");
            return BOOKS.stream().filter(book -> book.getId().equals(bookId)).findFirst().orElse(null);
        };
    }

    public static DataFetcher<Author> getAuthorDataFetcher() {
        return dataFetchingEnvironment -> {
            Book book = dataFetchingEnvironment.getSource();
            String authorId = book.getAuthorId();
            return AUTHORS.stream().filter(author -> author.getId().equals(authorId)).findFirst().orElse(null);
        };
    }

    public static DataFetcher<Book> addBookDataFetcher() {
        return dataFetchingEnvironment -> {
            Map<String, Object> bookInput = dataFetchingEnvironment.getArgument("bookInput");
            String id = "book-" + (BOOKS.size() + 1);
            String name = (String) bookInput.get("name");
            String authorId = (String) bookInput.get("authorId");
            Book book = new Book(id, name, authorId);
            return book;
        };
    }
}
