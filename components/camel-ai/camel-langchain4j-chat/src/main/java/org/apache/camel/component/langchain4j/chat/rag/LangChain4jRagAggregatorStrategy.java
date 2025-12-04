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

package org.apache.camel.component.langchain4j.chat.rag;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChatHeaders.AUGMENTED_DATA;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.DefaultContent;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

public class LangChain4jRagAggregatorStrategy implements AggregationStrategy {
    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // In theory old exchange shouldn't be null
        if (oldExchange == null) {
            return newExchange;
        }

        // check that we got new Augmented Data
        Optional<List<String>> newAugmentedData =
                Optional.ofNullable(newExchange.getIn().getBody(List.class));
        if (newAugmentedData.isEmpty()) {
            return oldExchange;
        }

        // create a list of contents from the retrieved Strings
        List<Content> newContents =
                newAugmentedData.get().stream().map(DefaultContent::new).collect(Collectors.toList());

        // Get or create the augmented data list from the old exchange
        List<Content> augmentedData = Optional.ofNullable(oldExchange.getIn().getHeader(AUGMENTED_DATA, List.class))
                .orElse(new ArrayList<Content>());
        augmentedData.addAll(newContents);

        // add the retrieved data in the body, langchain4j-chat will know it has to add inside the body
        oldExchange.getIn().setHeader(AUGMENTED_DATA, augmentedData);

        return oldExchange;
    }
}
