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

import java.util.Collections;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.camel.example.cxf.jaxrs.resources.BookNotFoundFault;
import org.apache.camel.example.cxf.jaxrs.resources.BookStore;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.ResponseExceptionMapper;

public final class JAXRSClient {
    
    private BookStore bookStore;
    
    public JAXRSClient() {       
        bookStore = JAXRSClientFactory.create(
            "http://localhost:" + System.getProperty("restEndpointPort") + "/rest",
            BookStore.class,
            Collections.singletonList(new TestResponseExceptionMapper()));        
    }
    
    public BookStore getBookStore() {
        return bookStore;
    }
    
    public static class TestResponseExceptionMapper implements ResponseExceptionMapper<BookNotFoundFault> {
        
        public TestResponseExceptionMapper() {
        }
        
        public BookNotFoundFault fromResponse(Response r) {
            Object value = r.getMetadata().getFirst("BOOK-HEADER");
            if (value != null) {
                return new BookNotFoundFault(value.toString());
            }
            throw new WebApplicationException();
        }
        
    }

}
