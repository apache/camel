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
package org.apache.camel.component.langchain4j.ingest;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.Experimental;
import org.apache.camel.Processor;

/**
 * Decodes the bytes of Tika's plain-text output as the UTF-8 the parse step pinned with
 * {@code tikaParseOutputEncoding}. This step exists because a plain {@code getBody(String.class)} resolves its charset
 * through the exchange: the {@code CamelCharsetName} <em>header</em> first, then the exchange property — so any route
 * that lets a message-supplied {@code CamelCharsetName} header survive up to the conversion hands the decode to whoever
 * sent the message. Reading the raw bytes and decoding as the pinned charset never consults that heuristic. Note that
 * {@code convertBodyTo(String.class, "UTF-8")} would not be a safe substitute: the option sets the exchange
 * <em>property</em>, which the injected header outranks.
 *
 * <p>
 * camel-tika itself no longer forwards Camel-namespace metadata names from the parsed document (CAMEL-24423), so the
 * document-side injection path is closed there; this decode remains correct by construction against a
 * {@code CamelCharsetName} delivered with the message by the consumer, or by any component without that filter.
 */
@Experimental
public class TikaTextDecode implements Processor {

    @Override
    public void process(Exchange exchange) {
        byte[] text = exchange.getMessage().getBody(byte[].class);
        // a parse yielding no body flows on as blank text and becomes the EMPTY outcome,
        // instead of an opaque NullPointerException here
        exchange.getMessage().setBody(text == null ? "" : new String(text, StandardCharsets.UTF_8).strip());
    }
}
