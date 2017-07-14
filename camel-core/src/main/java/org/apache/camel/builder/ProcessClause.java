/**
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
package org.apache.camel.builder;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class ProcessClause<T> implements Processor {
    private final T parent;
    private Processor processor;

    public ProcessClause(T parent) {
        this.parent = parent;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (processor != null) {
            processor.process(exchange);
        }
    }

    // *******************************
    // Exchange
    // *******************************

    /**
     * Define a {@link Processor} which targets the Exchange.
     */
    public T exchange(final Consumer<Exchange> consumer) {
        processor = consumer::accept;
        return parent;
    }


    // *******************************
    // Message
    // *******************************
    
    /**
     * Define a {@link Processor} which targets the Exchange In Message.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .process()
     *         .message(m -> m.setHeader("HasBody", m.getBody() != null));
     * }</pre></blockquote>
     */
    public T message(final Consumer<Message> consumer) {
        processor = e -> consumer.accept(e.getIn());
        return parent;
    }

    // *******************************
    // Body
    // *******************************

    /**
     * Define a {@link Processor} which targets the Exchange In Body.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .process()
     *         .body(System.out::println);
     * }</pre></blockquote>
     */
    public T body(final Consumer<Object> consumer) {
        processor = e -> consumer.accept(e.getIn().getBody());
        return parent;
    }

    /**
     * Define a {@link Processor} which targets the typed Exchange In Body.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .process()
     *         .body(MyObject.class, MyObject::dumpToStdOut);
     * }</pre></blockquote>
     */
    public <B> T body(Class<B> type, final Consumer<B> consumer) {
        processor = e -> consumer.accept(e.getIn().getBody(type));
        return parent;
    }
    
    /**
     * Define a {@link Processor} which targets the Exchange In Body and its Headers.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .process()
     *         .body((b, h) -> h.put("ClassName", b.getClass().getName()));
     * }</pre></blockquote>
     */
    public T body(final BiConsumer<Object, Map<String, Object>> consumer) {
        processor = e -> consumer.accept(
            e.getIn().getBody(),
            e.getIn().getHeaders()
        );
        return parent;
    }
    
    /**
     * Define a {@link Processor} which targets the typed Exchange In Body and its Headers.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .process()
     *         .body(MyObject.class, (b, h) -> { 
     *             if (h.containsKey("dump")) {
     *                  b.dumpToStdOut();
     *             }
     *         });
     * }</pre></blockquote>
     */
    public <B> T body(Class<B> type, final BiConsumer<B, Map<String, Object>> consumer) {
        processor = e -> consumer.accept(
            e.getIn().getBody(type),
            e.getIn().getHeaders()
        );
        return parent;
    }
}
