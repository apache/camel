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
package org.apache.camel.component.lucene;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.lucene.support.Hits;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneIndexAndQueryProducerTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(LuceneIndexAndQueryProducerTest.class);

    private String[] humorousQuotes = {
        "I think, therefore I am. I think - George Carlin",
        "I have as much authority as the Pope. I just don't have as many people who believe it. - George Carlin",
        "There`s no present. There`s only the immediate future and the recent past - George Carlin",
        "Politics doesn't make strange bedfellows - marriage does. - Groucho Marx",
        "I refuse to join any club that would have me as a member. - Groucho Marx",
        "I tell ya when I was a kid, all I knew was rejection. My yo-yo, it never came back. - Rodney Dangerfield",
        "I worked in a pet store and people kept asking how big I'd get. - Rodney Dangerfield"
    };

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("std", new File("target/stdindexDir"));
        registry.bind("load_dir", new File("src/test/resources/sources"));
        registry.bind("stdAnalyzer", new StandardAnalyzer());
        registry.bind("simple", new File("target/simpleindexDir"));
        registry.bind("simpleAnalyzer", new SimpleAnalyzer());
        registry.bind("whitespace", new File("target/whitespaceindexDir"));
        registry.bind("whitespaceAnalyzer", new WhitespaceAnalyzer());
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendRequest(final String quote) throws Exception {

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {                
                    
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(quote, String.class);
            }            
        });
    }
 
    private void sendQuery() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {                
                    
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setHeader("QUERY", "");
            }            
        });
    }
    
    @Test
    public void testLuceneIndexProducer() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");

        context.stop();
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    to("lucene:stdQuotesIndex:insert?analyzer=#stdAnalyzer&indexDir=#std&srcDir=#load_dir").
                    to("lucene:simpleQuotesIndex:insert?analyzer=#simpleAnalyzer&indexDir=#simple&srcDir=#load_dir").
                    to("lucene:whitespaceQuotesIndex:insert?analyzer=#whitespaceAnalyzer&indexDir=#whitespace&srcDir=#load_dir").
                    to("mock:result");

            }
        });
        context.start();
        
        LOG.debug("------------Beginning LuceneIndexProducer Test---------------");
        for (String quote : humorousQuotes) {
            sendRequest(quote);
        }
        
        mockEndpoint.assertIsSatisfied();
        LOG.debug("------------Completed LuceneIndexProducer Test---------------");
        context.stop();
    }    

    @Test
    public void testLucenePhraseQueryProducer() throws Exception {
        MockEndpoint mockSearchEndpoint = getMockEndpoint("mock:searchResult");
        
        context.stop();
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                
                from("direct:start").
                    setHeader("QUERY", constant("Seinfeld")).
                    to("lucene:searchIndex:query?analyzer=#whitespaceAnalyzer&indexDir=#whitespace&maxHits=20").
                    to("direct:next");
                
                from("direct:next").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Hits hits = exchange.getIn().getBody(Hits.class);
                        printResults(hits);
                    }

                    private void printResults(Hits hits) {
                        LOG.debug("Number of hits: " + hits.getNumberOfHits());
                        for (int i = 0; i < hits.getNumberOfHits(); i++) {
                            LOG.debug("Hit " + i + " Index Location:" + hits.getHit().get(i).getHitLocation());
                            LOG.debug("Hit " + i + " Score:" + hits.getHit().get(i).getScore());
                            LOG.debug("Hit " + i + " Data:" + hits.getHit().get(i).getData());
                        }
                    }
                }).to("mock:searchResult");
            }
        });
        context.start();
        LOG.debug("------------Beginning LuceneQueryProducer Phrase Test---------------");
        
        sendQuery();
        mockSearchEndpoint.assertIsSatisfied();
        LOG.debug("------------Completed LuceneQueryProducer Phrase Test---------------");
        context.stop();
    }    
    
    @Test
    public void testLuceneWildcardQueryProducer() throws Exception {
        MockEndpoint mockSearchEndpoint = getMockEndpoint("mock:searchResult");
        
        context.stop();
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                
                from("direct:start").
                    setHeader("QUERY", constant("Grouc?? Marx")).
                    to("lucene:searchIndex:query?analyzer=#stdAnalyzer&indexDir=#std&maxHits=20").
                    to("direct:next");
                
                from("direct:next").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Hits hits = exchange.getIn().getBody(Hits.class);
                        printResults(hits);
                    }
                    
                    private void printResults(Hits hits) {
                        LOG.debug("Number of hits: " + hits.getNumberOfHits());
                        for (int i = 0; i < hits.getNumberOfHits(); i++) {
                            LOG.debug("Hit " + i + " Index Location:" + hits.getHit().get(i).getHitLocation());
                            LOG.debug("Hit " + i + " Score:" + hits.getHit().get(i).getScore());
                            LOG.debug("Hit " + i + " Data:" + hits.getHit().get(i).getData());
                        }
                    }
                }).to("mock:searchResult");
            }
        });
        context.start();
        LOG.debug("------------Beginning  LuceneQueryProducer Wildcard Test---------------");
        
        sendQuery();
        mockSearchEndpoint.assertIsSatisfied();
        LOG.debug("------------Completed LuceneQueryProducer Wildcard Test---------------");
        context.stop();
    }

    @Test
    public void testReturnLuceneDocsQueryProducer() throws Exception {
        MockEndpoint mockSearchEndpoint = getMockEndpoint("mock:searchResult");
        context.stop();
        context.addRoutes(new RouteBuilder() {
            public void configure() {

                from("direct:start").
                        setHeader("QUERY", constant("Grouc?? Marx")).
                        setHeader("RETURN_LUCENE_DOCS", constant("true")).
                        to("lucene:searchIndex:query?analyzer=#stdAnalyzer&indexDir=#std&maxHits=20").
                        to("direct:next");

                from("direct:next").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Hits hits = exchange.getIn().getBody(Hits.class);
                        printResults(hits);
                    }

                    private void printResults(Hits hits) throws Exception {
                        LOG.debug("Number of hits: " + hits.getNumberOfHits());
                        for (int i = 0; i < hits.getNumberOfHits(); i++) {
                            LOG.debug("Hit " + i + " Index Location:" + hits.getHit().get(i).getHitLocation());
                            LOG.debug("Hit " + i + " Score:" + hits.getHit().get(i).getScore());
                            LOG.debug("Hit " + i + " Data:" + hits.getHit().get(i).getData());
                            if (hits.getHit().get(i).getDocument() == null) {
                                throw new Exception("Failed to return lucene documents");
                            }
                        }
                    }
                }).to("mock:searchResult").process(exchange -> {
                    Hits hits = exchange.getIn().getBody(Hits.class);
                    if (hits == null) {
                        HashMap<String, String> map = new HashMap<>();
                        map.put("NO_LUCENE_DOCS_ERROR", "NO LUCENE DOCS FOUND");
                        exchange.getContext().setGlobalOptions(map);
                    }
                    LOG.debug("Number of hits: " + hits.getNumberOfHits());
                });
            }
        });
        context.start();
        LOG.debug("------------Beginning  LuceneQueryProducer Wildcard with Return Lucene Docs Test---------------");

        sendQuery();
        mockSearchEndpoint.assertIsSatisfied();
        Map<String, String> errorMap = mockSearchEndpoint.getCamelContext().getGlobalOptions();
        LOG.debug("------------Completed LuceneQueryProducer Wildcard with Return Lucene Docs Test---------------");
        context.stop();
        assertTrue(errorMap.get("NO_LUCENE_DOCS_ERROR") == null);
    }
}
