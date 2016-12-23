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

package org.apache.camel.component.dns.processor.remote;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.dns.DnsConfiguration;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

public class DnsServiceLookupFactory implements Function<String, Lookup> {
    private final DnsConfiguration configuration;
    private final ConcurrentHashMap<String, Lookup> cache;

    public DnsServiceLookupFactory(DnsConfiguration configuration) {
        this.configuration = configuration;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Lookup apply(String name) {
        return cache.computeIfAbsent(name, this::createLookup);
    }

    private Lookup createLookup(String name) {
        try {
            return new Lookup(
                String.format("%s.%s.%s", name, configuration.getProto(), configuration.getDomain()),
                Type.SRV);
        } catch (TextParseException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
