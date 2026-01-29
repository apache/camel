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
package org.apache.camel.test.infra.ollama.services;

/**
 * Test service for OpenAI and OpenAI-compatible endpoints.
 * <p>
 * Use this service when testing with OpenAI API or compatible providers. Configure via:
 * <ul>
 * <li>{@code -Dollama.instance.type=openai}</li>
 * <li>{@code -Dollama.api.key=sk-xxx}</li>
 * <li>{@code -Dollama.model=gpt-4o-mini} (optional, defaults to gpt-4o-mini)</li>
 * <li>{@code -Dollama.endpoint=https://api.openai.com/v1/} (optional)</li>
 * </ul>
 */
public class OpenAIService extends OpenAIInfraService implements OllamaService {
}
