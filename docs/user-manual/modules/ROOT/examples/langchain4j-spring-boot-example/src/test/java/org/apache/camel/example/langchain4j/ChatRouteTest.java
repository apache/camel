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
package org.apache.camel.example.langchain4j;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for ChatRoute demonstrating LangChain4j Spring Boot integration.
 * 
 * Note: This test requires a valid OpenAI API key or Ollama running locally.
 * Set OPENAI_API_KEY environment variable or use the 'dev' profile with Ollama.
 */
@CamelSpringBootTest
@SpringBootTest(classes = Application.class)
public class ChatRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Test
    public void testCamelContextStarted() {
        assertNotNull(camelContext);
        assertTrue(camelContext.isStarted());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void testSimpleChat() {
        String response = producerTemplate.requestBody("direct:chat", 
            "What is Apache Camel?", String.class);
        
        assertNotNull(response);
        assertTrue(response.length() > 0);
        System.out.println("LLM Response: " + response);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void testChatWithTemplate() {
        // This test would require setting up the template in headers
        // Simplified version for demonstration
        String response = producerTemplate.requestBody("direct:chat", 
            "Explain integration patterns", String.class);
        
        assertNotNull(response);
        assertTrue(response.length() > 0);
    }
}

