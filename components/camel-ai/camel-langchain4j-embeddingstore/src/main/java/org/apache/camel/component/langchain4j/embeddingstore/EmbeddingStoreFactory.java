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

package org.apache.camel.component.langchain4j.embeddingstore;

import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.camel.CamelContextAware;

/**
 * Factory interface for creating EmbeddingStore instances within the Apache Camel LangChain4j integration.
 *
 * <p>
 * The factory extends {@link CamelContextAware} to ensure proper integration with the Camel context and access to
 * registry components.
 * </p>
 *
 * @since 4.14.0
 */
public interface EmbeddingStoreFactory extends CamelContextAware {

    EmbeddingStore createEmbeddingStore() throws Exception;
}
