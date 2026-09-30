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
package org.apache.camel.java.in;

import java.util.List;

/**
 * Turns a call of the endpoint DSL, {@code kafka("orders").brokers("b:9092")}, into the endpoint URI it stands for,
 * {@code kafka://orders?brokers=b:9092}, without the endpoint DSL on the class path.
 * <p/>
 * {@link #NAMING} works by the rules the endpoint DSL is generated with, and needs nothing. A tool with the Camel
 * catalog can plug in its own (see {@link LwJavaParser#setEndpointDslResolver(EndpointDslResolver)}) to also confirm
 * the component exists, check option names, and know the prefix of multi-value options.
 */
public interface EndpointDslResolver {

    /**
     * An option call on the endpoint builder: {@code brokers("b:9092")} has one value; a multi-value option,
     * {@code schedulerProperties("delay", 1000)}, has the key and the value.
     */
    record Option(String name, List<String> values) {
    }

    /**
     * The endpoint: its URI, and what the resolver could not settle (each is reported as unresolved).
     */
    record Endpoint(String uri, List<String> problems) {
    }

    /**
     * The endpoint of an endpoint DSL call, or null when {@code factory} is not an endpoint of the endpoint DSL.
     *
     * @param factory the factory method, such as {@code kafka} or {@code platformHttp}
     * @param args    its arguments: the path, or the component name and the path
     * @param options the option calls after it, in order
     */
    Endpoint endpoint(String factory, List<String> args, List<Option> options);

    /** The rules the endpoint DSL is generated with; the default. */
    EndpointDslResolver NAMING = new NamingEndpointDslResolver();
}
