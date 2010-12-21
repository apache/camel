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
package org.apache.camel.component.routebox.demo;

import java.util.HashMap;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BookCatalog {
    private static final transient Log LOG = LogFactory.getLog(BookCatalog.class);
    private HashMap<String, Book> map;

    public BookCatalog() {
        super();
        this.map = new HashMap<String, Book>();
    }
    
    public BookCatalog(HashMap<String, Book> map) {
        super();
        this.map = map;
    }

    public String addToCatalog(Book book) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding book with author " + book.getAuthor() + " and title " + book.getTitle());
        }
        map.put(book.getAuthor(), book);
        return "Book with Author " + book.getAuthor() + " and title " + book.getTitle() + " added to Catalog";
    }
    
    public Book findBook(String author) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding book with author " + author);
        }
        return map.get(author);
    }
    
    public Book findAuthor(String title) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding author with book " + title);
        }
        Set<String> keys = map.keySet();
        Book book = null;
        
        for (String key : keys) {
            if (map.get(key).getTitle().equalsIgnoreCase(title)) {
                book = map.get(key);
            }
        }
        
        return book;
    }

    public void initialize() throws Exception {
        map.clear();
    }
    
    public boolean isEmpty() {
        return map.isEmpty();
    }
}
