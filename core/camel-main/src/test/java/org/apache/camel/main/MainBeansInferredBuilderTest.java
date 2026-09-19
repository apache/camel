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
package org.apache.camel.main;

import java.time.Duration;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24820: camel.beans.x = #class:... of a class with no public constructor but a builder() sets the properties of
 * the bean on the builder before the bean is built.
 */
public class MainBeansInferredBuilderTest {

    @Test
    public void testBeanViaInferredBuilder() {
        Main main = new Main();
        main.addProperty("camel.beans.chatModel", "#class:" + ChatModel.class.getName());
        main.addProperty("camel.beans.chatModel.base-url", "http://localhost:11434");
        main.addProperty("camel.beans.chatModel.modelName", "qwen2.5");
        main.addProperty("camel.beans.chatModel.timeout", "2m");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        ChatModel model = assertInstanceOf(ChatModel.class, camelContext.getRegistry().lookupByName("chatModel"));
        assertEquals("http://localhost:11434", model.baseUrl);
        assertEquals("qwen2.5", model.modelName);
        assertEquals(Duration.ofMinutes(2), model.timeout);

        main.stop();
    }

    @Test
    public void testBeanViaInferredBuilderWithoutProperties() {
        Main main = new Main();
        main.addProperty("camel.beans.chatModel", "#class:" + ChatModel.class.getName());
        main.start();

        ChatModel model = assertInstanceOf(ChatModel.class, main.getCamelContext().getRegistry().lookupByName("chatModel"));
        assertNull(model.modelName);
        assertEquals(Duration.ofSeconds(60), model.timeout);

        main.stop();
    }

    @Test
    public void testUnknownPropertyFails() {
        Main main = new Main();
        main.addProperty("camel.beans.chatModel", "#class:" + ChatModel.class.getName());
        main.addProperty("camel.beans.chatModel.model", "qwen2.5");

        Exception e = assertThrows(Exception.class, main::start);
        String msg = e.getMessage() + (e.getCause() != null ? " " + e.getCause().getMessage() : "");
        assertTrue(msg.contains("model"), msg);
    }

    /** The shape of a LangChain4j model: no public constructor, builder(), nested builder with build() */
    public static final class ChatModel {
        final String baseUrl;
        final String modelName;
        final Duration timeout;

        private ChatModel(Builder b) {
            this.baseUrl = b.baseUrl;
            this.modelName = b.modelName;
            this.timeout = b.timeout;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String baseUrl;
            private String modelName;
            private Duration timeout = Duration.ofSeconds(60);

            public Builder baseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
                return this;
            }

            public Builder modelName(String modelName) {
                this.modelName = modelName;
                return this;
            }

            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public ChatModel build() {
                return new ChatModel(this);
            }
        }
    }
}
