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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.routebox.demo.Book;
import org.apache.camel.component.routebox.demo.BookCatalog;
import org.apache.camel.component.routebox.demo.RouteboxDemoTestSupport;
import org.apache.camel.component.routebox.demo.SimpleRouteBuilder;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteboxDispatchMapTest extends RouteboxDemoTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RouteboxSedaTest.class);
    
    private ProducerTemplate template;
    private String routeboxUri = "routebox:multipleRoutes?innerRegistry=#registry&routeBuilders=#routes&dispatchMap=#map"; 
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = new JndiRegistry(createJndiContext());
        
        // Wire the routeDefinitions & dispatchStrategy to the outer camelContext where the routebox is declared
        List<RouteBuilder> routes = new ArrayList<RouteBuilder>();
        routes.add(new SimpleRouteBuilder());
        registry.bind("registry", createInnerRegistry());
        registry.bind("routes", routes);
        
        // Wire a dispatch map to registry
        Map<String, String> map = new HashMap<String, String>();
        map.put("addToCatalog", "seda:addToCatalog");
        map.put("findBook", "seda:findBook");
        registry.bind("map", map);
        
        return registry;
    }
    
    private JndiRegistry createInnerRegistry() throws Exception {
        JndiRegistry innerRegistry = new JndiRegistry(createJndiContext());
        BookCatalog catalogBean = new BookCatalog();
        innerRegistry.bind("library", catalogBean);        
        
        return innerRegistry;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
    
    @Test
    public void testRouteboxUsingDispatchMap() throws Exception {
        template = new DefaultProducerTemplate(context);
        template.start();        
        
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(routeboxUri)
                    .to("log:Routes operation performed?showAll=true");
            }
        });
        context.start();
        
        LOG.debug("Beginning Test ---> testRouteboxUsingDispatchMap()");        
        
        Book book = new Book("Sir Arthur Conan Doyle", "The Adventures of Sherlock Holmes");

        String response = sendAddToCatalogRequest(template, routeboxUri, "addToCatalog", book);
        assertEquals("Book with Author " + book.getAuthor() + " and title " + book.getTitle() + " added to Catalog", response);
        
        book = sendFindBookRequest(template, routeboxUri, "findBook", "Sir Arthur Conan Doyle");
        LOG.debug("Received book with author {} and title {}", book.getAuthor(), book.getTitle());       
        assertEquals("The Adventures of Sherlock Holmes", book.getTitle());
        
        LOG.debug("Completed Test ---> testRouteboxUsingDispatchMap()");
        context.stop();
    }  
    
}
