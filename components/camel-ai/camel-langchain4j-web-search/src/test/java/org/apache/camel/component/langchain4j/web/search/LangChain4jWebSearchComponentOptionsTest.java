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
package org.apache.camel.component.langchain4j.web.search;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The search options can be set on the component, as camel.component.langchain4j-web-search.* properties do, and an
 * endpoint without options uses them.
 */
public class LangChain4jWebSearchComponentOptionsTest extends CamelTestSupport {

    private final AtomicReference<WebSearchRequest> componentEngineRequest = new AtomicReference<>();
    private final AtomicReference<WebSearchRequest> otherEngineRequest = new AtomicReference<>();

    private final WebSearchEngine componentEngine = request -> {
        componentEngineRequest.set(request);
        return results();
    };

    private final WebSearchEngine otherEngine = request -> {
        otherEngineRequest.set(request);
        return results();
    };

    private static WebSearchResults results() {
        return WebSearchResults.from(
                WebSearchInformationResult.from(2L),
                List.of(WebSearchOrganicResult.from("Title", URI.create("https://example.com"), "snippet", "content"),
                        WebSearchOrganicResult.from("Title 2", URI.create("https://example.org"), "snippet 2",
                                "content 2")));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // two engines in the registry, so that the endpoint cannot autowire one: the engine must come from the component
        context.getRegistry().bind("componentEngine", componentEngine);
        context.getRegistry().bind("otherEngine", otherEngine);

        LangChain4jWebSearchComponent component
                = context.getComponent(LangChain4jWebSearchEngine.SCHEME, LangChain4jWebSearchComponent.class);
        Map<String, Object> properties = new HashMap<>();
        properties.put("webSearchEngine", "#componentEngine");
        properties.put("maxResults", "2");
        properties.put("resultType", "SNIPPET");
        properties.put("language", "de");
        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(component)
                .withConfigurer(component.getComponentPropertyConfigurer())
                .withProperties(properties)
                .withMandatory(true)
                .bind();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:component")
                        .to("langchain4j-web-search:component");

                from("direct:override")
                        .to("langchain4j-web-search:override?maxResults=1&resultType=CONTENT&webSearchEngine=#otherEngine");
            }
        };
    }

    @Test
    void endpointWithoutOptionsUsesTheComponentOptions() {
        Object body = template.requestBody("direct:component", "apache camel");

        WebSearchRequest request = componentEngineRequest.get();
        assertNotNull(request);
        assertEquals("apache camel", request.searchTerms());
        assertEquals(2, request.maxResults());
        assertEquals("de", request.language());
        assertEquals(List.of("snippet", "snippet 2"), body);
        assertNull(otherEngineRequest.get());
    }

    @Test
    void endpointOptionsOverrideTheComponentOptions() {
        Object body = template.requestBody("direct:override", "apache camel");

        WebSearchRequest request = otherEngineRequest.get();
        assertNotNull(request);
        assertEquals(1, request.maxResults());
        // not set on the endpoint, so the component value is used
        assertEquals("de", request.language());
        assertEquals("content", body);
        assertNull(componentEngineRequest.get());
    }
}
