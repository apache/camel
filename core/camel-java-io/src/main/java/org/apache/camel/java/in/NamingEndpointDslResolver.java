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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The endpoint DSL by the rules it is generated with: the factory is the component scheme in camel case
 * ({@code platformHttp} is {@code platform-http}), its argument is the path, and each builder method is the option of
 * the same name. The URI is built as the endpoint DSL builds it for producers and consumers alike:
 * {@code scheme://path?option=value&...}.
 * <p/>
 * Without the catalog it cannot tell a component from any other method of that name, so the parser only asks it for
 * sources that use the endpoint DSL; nor does it know the prefix of a multi-value option.
 */
final class NamingEndpointDslResolver implements EndpointDslResolver {

    /** Schemes the camel case rule cannot give back: names that are Java keywords or have other characters. */
    private static final Map<String, String> EXCEPTIONS = Map.of(
            "clas", "class", "coapTcp", "coap+tcp", "coapsTcp", "coaps+tcp", "restEndpoint", "rest");

    private static final Pattern UPPER = Pattern.compile("(?<=[a-z0-9])([A-Z])");

    /** Switches between the basic and advanced options of a builder: nothing for the URI. */
    private static final List<String> SWITCHES = List.of("advanced", "basic");

    /** The scheme of a factory method. */
    static String scheme(String factory) {
        String exception = EXCEPTIONS.get(factory);
        if (exception != null) {
            return exception;
        }
        return UPPER.matcher(factory).replaceAll(m -> "-" + m.group(1).toLowerCase());
    }

    @Override
    public Endpoint endpoint(String factory, List<String> args, List<Option> options) {
        if (args.isEmpty() || args.size() > 2) {
            return null;
        }
        // kafka("orders"), or kafka("myKafka", "orders") for a component of another name
        String scheme = args.size() == 2 ? args.get(0) : scheme(factory);
        String path = args.get(args.size() - 1);
        List<String> problems = new ArrayList<>();
        StringBuilder query = new StringBuilder();
        for (Option o : options) {
            if (SWITCHES.contains(o.name()) && o.values().isEmpty()) {
                continue;
            }
            if (o.values().size() != 1) {
                problems.add(o.values().size() == 2
                        ? "the multi-value option " + o.name() + " needs the catalog to know its prefix"
                        : "not an option: " + o.name());
                continue;
            }
            query.append(query.isEmpty() ? "" : "&").append(o.name()).append('=').append(o.values().get(0));
        }
        String uri = scheme + "://" + path;
        if (!query.isEmpty()) {
            uri += (path.contains("?") ? "&" : "?") + query;
        }
        return new Endpoint(uri, problems);
    }
}
