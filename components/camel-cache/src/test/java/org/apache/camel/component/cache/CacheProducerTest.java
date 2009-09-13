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

package org.apache.camel.component.cache;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class CacheProducerTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CacheProducerTest.class);
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendFile() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
             // Read from an input stream
                InputStream is = new BufferedInputStream(
                    new FileInputStream("./src/test/resources/test.txt"));

                byte buffer[] = IOConverter.toBytes(is);
                is.close();
                
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }            
        });
    }
    
    private void sendUpdatedFile() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
             // Read from an input stream
                InputStream is = new BufferedInputStream(
                    new FileInputStream("./src/test/resources/updatedtest.txt"));

                byte buffer[] = IOConverter.toBytes(is);
                is.close();
                
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }            
        });
    }    

    @Test
    public void testAddingDataToCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    setHeader("CACHE_OPERATION", constant("ADD")).
                    setHeader("CACHE_KEY", constant("Ralph_Waldo_Emerson")).
                    to("cache://TestCache1");
            }
        });
        context.start();
        LOG.info("------------Beginning CacheProducer Add Test---------------");
        sendFile();
    }
    
    @Test
    public void testUpdatingDataInCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    setHeader("CACHE_OPERATION", constant("UPDATE")).
                    setHeader("CACHE_KEY", constant("Ralph_Waldo_Emerson")).
                    to("cache://TestCache1");
            }
        });
        context.start();
        LOG.info("------------Beginning CacheProducer Update Test---------------");
        sendUpdatedFile();
    }
    
    @Test
    public void testDeletingDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    setHeader("CACHE_OPERATION", constant("DELETE")).
                    setHeader("CACHE_KEY", constant("Ralph_Waldo_Emerson")).
                    to("cache://TestCache1");
            }
        });
        context.start();
        LOG.info("------------Beginning CacheProducer Delete Test---------------");
        sendUpdatedFile();
    }
    
    @Test
    public void testDeletingAllDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    setHeader("CACHE_OPERATION", constant("ADD")).
                    setHeader("CACHE_KEY", constant("Ralph_Waldo_Emerson")).
                    to("cache://TestCache1");
                from("direct:start").
                    setHeader("CACHE_OPERATION", constant("ADD")).
                    setHeader("CACHE_KEY", constant("Ralph_Waldo_Emerson2")).
                    to("cache://TestCache1");
                from("direct:start").
                    setHeader("CACHE_OPERATION", constant("DELETEALL")).
                    to("cache://TestCache1");
            }
        });
        context.start();
        LOG.info("------------Beginning CacheProducer Delete All Elements Test---------------");
        sendUpdatedFile();
    }    
    
}
