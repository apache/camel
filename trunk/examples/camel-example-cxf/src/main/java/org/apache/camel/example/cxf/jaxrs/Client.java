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
package org.apache.camel.example.cxf.jaxrs;

import org.apache.camel.example.cxf.jaxrs.resources.Book;
import org.apache.camel.example.cxf.jaxrs.resources.BookNotFoundFault;
import org.apache.camel.example.cxf.jaxrs.resources.BookStore;

public class Client {
    
    void invoke() throws BookNotFoundFault {
        // JAXWSClient invocation
        JAXWSClient jaxwsClient = new JAXWSClient();
        BookStore bookStore = jaxwsClient.getBookStore();
        
        bookStore.addBook(new Book("Camel User Guide", 123L));
        Book book = bookStore.getBook(123L);
        System.out.println("Get the book with id 123. " + book);       
      
        try {
            book = bookStore.getBook(124L);
            System.out.println("Get the book with id 124. " + book); 
        } catch (Exception exception) {
            System.out.println("Expected exception received: " + exception);
        }
        
        // JAXRSClient invocation
        JAXRSClient jaxrsClient = new JAXRSClient();
        bookStore =  jaxrsClient.getBookStore();
        
        bookStore.addBook(new Book("Karaf User Guide", 124L));
        book = bookStore.getBook(124L);
        System.out.println("Get the book with id 124. " + book);
        
        try {
            book = bookStore.getBook(126L);
            System.out.println("Get the book with id 126. " + book); 
        } catch (Exception exception) {
            System.out.println("Expected exception received: " + exception);
        }
    }
    
    public static void main(String args[]) throws Exception {
        Client client = new Client();
        client.invoke();
    }

}
