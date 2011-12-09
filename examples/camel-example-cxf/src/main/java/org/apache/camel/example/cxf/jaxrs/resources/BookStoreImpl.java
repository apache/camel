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
package org.apache.camel.example.cxf.jaxrs.resources;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public class BookStoreImpl implements BookStore {
    
    private Map<Long, Book> books = new HashMap<Long, Book>();
    private boolean isRest;
    
    public BookStoreImpl(boolean restFlag) {
        isRest = restFlag;
        init();        
    }
    
    public BookStoreImpl() {        
        init();        
    }
    
    public Book getBook(Long id) throws BookNotFoundFault {
        
        if (books.get(id) == null) {
            BookNotFoundDetails details = new BookNotFoundDetails();
            details.setId(id);
            if (!isRest) {
                throw new BookNotFoundFault("Can't find the Book with id " + id, details);
            } else {                
                Response r = Response.status(404).header("BOOK-HEADER",
                    "No Book with id " + id + " is available").entity(details).build();
                throw new WebApplicationException(r);
            }
        }
        
        return books.get(id);
    }
    
    public Book addBook(Book book) {        
        books.put(book.getId(), book);
        return books.get(book.getId());
    }
    
    private void init() {
        Book book = new Book();
        book.setId(101);
        book.setName("CXF User Guide");
        books.put(book.getId(), book);
    }    

}
