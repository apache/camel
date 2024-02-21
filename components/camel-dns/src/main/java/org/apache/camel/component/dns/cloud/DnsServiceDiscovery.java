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
package org.apache.camel.component.dns.cloud;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.dns.DnsConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.apache.camel.util.ObjectHelper;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

public final class DnsServiceDiscovery extends DefaultServiceDiscovery {
    private static final Comparator<SRVRecord> COMPARATOR = comparator();
    private final DnsConfiguration configuration;
    private final ConcurrentHashMap<String, Lookup> cache;

    public DnsServiceDiscovery(DnsConfiguration configuration) {
        this.configuration = configuration;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        final Lookup lookup = cache.computeIfAbsent(name, this::createLookup);
        final Record[] records = lookup.run();

        List<ServiceDefinition> services;
        if (Objects.nonNull(records) && lookup.getResult() == Lookup.SUCCESSFUL) {
            services = Arrays.stream(records)
                    .filter(SRVRecord.class::isInstance)
                    .map(SRVRecord.class::cast)
                    .sorted(COMPARATOR)
                    .map(srvRecord -> asService(name, srvRecord))
                    .collect(Collectors.toList());
        } else {
            services = Collections.emptyList();
        }

        return services;
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

    private static Comparator<SRVRecord> comparator() {
        Comparator<SRVRecord> byPriority = (e1, e2) -> Integer.compare(e2.getPriority(), e1.getPriority());
        Comparator<SRVRecord> byWeight = (e1, e2) -> Integer.compare(e2.getWeight(), e1.getWeight());

        return byPriority.thenComparing(byWeight);
    }

    private static ServiceDefinition asService(String serviceName, SRVRecord srvRecord) {
        Map<String, String> meta = new HashMap<>();
        ObjectHelper.ifNotEmpty(srvRecord.getPriority(), val -> meta.put("priority", Integer.toString(val)));
        ObjectHelper.ifNotEmpty(srvRecord.getWeight(), val -> meta.put("weight", Integer.toString(val)));

        return new DefaultServiceDefinition(
                serviceName,
                srvRecord.getTarget().toString(true),
                srvRecord.getPort(),
                meta);
    }
}
