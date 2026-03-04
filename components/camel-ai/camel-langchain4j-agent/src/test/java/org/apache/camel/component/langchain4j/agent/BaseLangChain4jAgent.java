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
package org.apache.camel.component.langchain4j.agent;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.apache.camel.component.langchain4j.agent.pojos.PersistentChatMemoryStore;
import org.apache.camel.test.junit6.CamelTestSupport;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.time.Duration.ofSeconds;

public class BaseLangChain4jAgent extends CamelTestSupport {

    protected ChatModel chatModel;
    protected ChatMemoryProvider chatMemoryProvider;
    protected RetrievalAugmentor retrievalAugmentor;
    protected PersistentChatMemoryStore chatMemoryStore;

    protected static final int MEMORY_ID_SESSION = 42;

    protected static final String USER_DATABASE = """
            {"id": "123", "name": "John Smith", "membership": "Gold", "rentals": 15, "preferredVehicle": "SUV"}
            """;

    protected static final String WEATHER_INFO = "Sunny, 22°C, perfect driving conditions";

    // Company knowledge base for RAG
    protected static final String COMPANY_KNOWLEDGE_BASE = """
            Miles of Camels Car Rental - Company Information

            BUSINESS HOURS:
            Monday-Friday: 8:00 AM - 6:00 PM
            Saturday: 9:00 AM - 4:00 PM
            Sunday: Closed

            RENTAL AGREEMENT
            - This agreement is between Miles of Camels Car Rental ("Company") and the customer ("Renter").

            RENTAL POLICIES:
            - Minimum age: 21 years old
            - Valid driver's license required
            - Credit card required for security deposit
            - Full tank of gas required at return

            VEHICLE FLEET:
            - Economy cars: Starting at $29/day
            - Mid-size cars: Starting at $39/day
            - SUVs: Starting at $59/day
            - Luxury vehicles: Starting at $89/day

            CANCELLATION POLICY
            - Cancellations made 24 hours before pickup: Full refund
            - Cancellations made 12-24 hours before pickup: 50% refund
            - Cancellations made less than 12 hours before pickup: No refund

            VEHICLE RETURN
            - Vehicles must be returned with the same fuel level as at pickup.
            - Late returns incur a fee of $25 per hour or fraction thereof.

            DAMAGE POLICY
            - Minor damages under $200: Covered by insurance
            - Major damages over $200: Customer responsibility

            INSURANCE
            - Basic insurance is included. Premium insurance available for $15/day.

            AGE REQUIREMENTS
            - Minimum age: 21 years old
            - Drivers under 25: Additional surcharge of $20/day

            """;

    protected ChatModel createChatModel(String apiKey, String baseUrl) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder();

        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }

        if (apiKey != null) {
            builder.apiKey(apiKey);
        }

        return builder.modelName(GPT_4_O_MINI)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    protected ChatMemoryProvider createMemoryProvider(ChatMemoryStore chatMemoryStore) {
        // Create a message window memory that keeps the last 10 messages
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(chatMemoryStore)
                .build();
        return chatMemoryProvider;
    }
}
