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

package org.apache.camel.component.langchain4j.tokenizer;

import org.apache.camel.component.langchain4j.tokenizer.config.LangChain4JConfiguration;
import org.apache.camel.component.langchain4j.tokenizer.util.SplitterTypes;

@org.apache.camel.spi.annotations.Tokenizer("langChain4jParagraphTokenizer")
public class LangChain4JParagraphTokenizer extends AbstractLangChain4JTokenizer<LangChain4JConfiguration> {

    @Override
    public Configuration newConfiguration() {
        return new LangChain4JConfiguration();
    }

    @Override
    public void configure(Configuration configuration) {
        configure(SplitterTypes.PARAGRAPH, (LangChain4JConfiguration) configuration);
    }

    @Override
    public String name() {
        return toName(SplitterTypes.PARAGRAPH);
    }
}
