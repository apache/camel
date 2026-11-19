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
package org.apache.camel.component.langchain4j.service.support;

/**
 * One turn a test expects of the model: the prompt LangChain4j is expected to render, and the reply to answer it with.
 * <p>
 * A conformance scenario declares its turns so that each backend can set itself up from the same description. The
 * OpenAI mock keys an expectation on the prompt, which makes the prompt itself an assertion: a rendering the component
 * did not produce matches nothing and the request fails naming what actually arrived. A real model ignores both fields
 * and answers as it likes, which is why the shared assertions stay weak enough to hold either way.
 */
public class ModelTurn {

    private final String prompt;
    private final String reply;

    public ModelTurn(String prompt, String reply) {
        this.prompt = prompt;
        this.reply = reply;
    }

    /**
     * @param  prompt the user message LangChain4j is expected to send, after it rendered any template
     * @param  reply  what the model answers to it
     * @return        the turn
     */
    public static ModelTurn turn(String prompt, String reply) {
        return new ModelTurn(prompt, reply);
    }

    public String prompt() {
        return prompt;
    }

    public String reply() {
        return reply;
    }

    @Override
    public String toString() {
        return "ModelTurn{prompt='" + prompt + "', reply='" + reply + "'}";
    }
}
