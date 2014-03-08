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
package org.apache.camel.component.routebox;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.routebox.demo.Book;
import org.apache.camel.component.routebox.demo.BookCatalog;
import org.apache.camel.component.routebox.demo.RouteboxDemoTestSupport;
import org.apache.camel.component.routebox.demo.SimpleRouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteboxDirectProducerOnlyTest extends RouteboxDemoTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RouteboxDirectProducerOnlyTest.class);
    
    private ProducerTemplate template;
    private String routeboxUri = "routebox:multipleRoutes?sendToConsumer=false&innerContext=#ctx&dispatchStrategy=#strategy"; 

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = new JndiRegistry(createJndiContext());
        
        // Wire the inner context & dispatchStrategy to the outer camelContext where the routebox is declared
        registry.bind("ctx", createInnerContext());        
        registry.bind("strategy", new SimpleRouteDispatchStrategy());
        return registry;
    }
    
    private CamelContext createInnerContext() throws Exception {
        // Create a camel context to be encapsulated by the routebox
        JndiRegistry innerRegistry = new JndiRegistry(createJndiContext());
        BookCatalog catalogBean = new BookCatalog();
        innerRegistry.bind("library", catalogBean);
        
        CamelContext innerContext = new DefaultCamelContext(innerRegistry);
        innerContext.addRoutes(new SimpleRouteBuilder());
        
        return innerContext;
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRouteboxDirectProducerOnlyRequests() throws Exception {
        template = new DefaultProducerTemplate(context);
        template.start();        
     
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to(routeboxUri)                    
                    .to("log:Routes operation performed?showAll=true");
            }
        });
        context.start();
        
        LOG.debug("Beginning Test ---> testRouteboxDirectSyncRequests()");       
        
        Book book = new Book("Sir Arthur Conan Doyle", "The Adventures of Sherlock Holmes");
        
        String response = sendAddToCatalogRequest(template, "direct:start", "addToCatalog", book);
        assertEquals("Book with Author " + book.getAuthor() + " and title " + book.getTitle() + " added to Catalog", response);

        //Thread.sleep(2000);
        
        book = sendFindBookRequest(template, "direct:start", "findBook", "Sir Arthur Conan Doyle");
        LOG.debug("Received book with author {} and title {}", book.getAuthor(), book.getTitle());        
        assertEquals("The Adventures of Sherlock Holmes", book.getTitle());
        
        LOG.debug("Completed Test ---> testRouteboxDirectSyncRequests()");
        context.stop();
    }      
}

