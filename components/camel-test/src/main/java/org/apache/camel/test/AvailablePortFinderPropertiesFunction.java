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
package org.apache.camel.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * A {@link PropertiesFunction} that reserves network ports and place them in a cache for reuse.
 * <p/>
 * The first time the function is invoked for a given name, an unused network port is determined and cached
 * in an hash map with the given name as key so each time this function is invoked for the same name, the
 * previously discovered port will be returned.
 * <p/>
 * This is useful for testing purpose where you can write a route like:
 * <pre>{@code
 * from("undertow:http://0.0.0.0:{{available-port:server-port}}")
 *     .to("mock:result");
 * }</pre>
 * And then you can invoke with {@link org.apache.camel.ProducerTemplate} like:
 * <pre>{@code
 * template.sendBody("undertow:http://0.0.0.0:{{available-port:server-port}}", "the body");
 * }</pre>
 * Doing so avoid the need to compute the port and pass it to the various method or store it as a global
 * variable in the test class.
 *
 * @see AvailablePortFinder
 */
public class AvailablePortFinderPropertiesFunction implements PropertiesFunction {
    private final Map<String, String> portMapping;

    public AvailablePortFinderPropertiesFunction() {
        this.portMapping = new ConcurrentHashMap<>();
    }

    @Override
    public String getName() {
        return "available-port";
    }

    @Override
    public String apply(String remainder) {
        if (ObjectHelper.isEmpty(remainder)) {
            return remainder;
        }

        String name = StringHelper.before(remainder, ":");
        String range = StringHelper.after(remainder, ":");

        if (name == null) {
            name = remainder;
        }

        final Integer from;
        final Integer to;

        if (range != null) {
            String f = StringHelper.before(range, "-");
            if (ObjectHelper.isEmpty(f)) {
                throw new IllegalArgumentException("Unable to parse from range, range should be defined in the as from-to, got: " + range);
            }

            String t = StringHelper.after(range, "-");
            if (ObjectHelper.isEmpty(t)) {
                throw new IllegalArgumentException("Unable to parse to range, range should be defined in the as from-to, got: " + range);
            }

            from = Integer.parseInt(f);
            to = Integer.parseInt(t);
        } else {
            from = null;
            to = null;
        }

        return this.portMapping.computeIfAbsent(name, n -> {
            final int port;

            if (from != null && to != null) {
                port = AvailablePortFinder.getNextAvailable(from, to);
            } else {
                port = AvailablePortFinder.getNextAvailable();
            }

            return Integer.toString(port);
        });
    }
}
