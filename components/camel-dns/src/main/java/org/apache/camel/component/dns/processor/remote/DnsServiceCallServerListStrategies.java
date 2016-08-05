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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.camel.component.dns.DnsConfiguration;
import org.apache.camel.spi.ServiceCallServer;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;


public final class DnsServiceCallServerListStrategies {
    private DnsServiceCallServerListStrategies() {
    }

    public static final class OnDemand extends DnsServiceCallServerListStrategy {
        private final DnsServiceLookupFactory lookupFactory;

        public OnDemand(DnsConfiguration configuration) throws Exception {
            super(configuration);
            this.lookupFactory = new DnsServiceLookupFactory(configuration);
        }

        @Override
        public List<ServiceCallServer> getUpdatedListOfServers(String name) {
            final Lookup lookup = lookupFactory.apply(name);
            final Record[] records = lookup.run();

            List<ServiceCallServer> servers;
            if (Objects.nonNull(records) && lookup.getResult() == Lookup.SUCCESSFUL) {
                servers = Arrays.stream(records)
                    .filter(SRVRecord.class::isInstance)
                    .map(SRVRecord.class::cast)
                    .sorted(DnsServiceCallServer.COMPARATOR)
                    .map(DnsServiceCallServer::new)
                    .collect(Collectors.toList());
            } else {
                servers = Collections.emptyList();
            }

            return servers;
        }

        @Override
        public String toString() {
            return "OnDemand";
        }
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static DnsServiceCallServerListStrategy onDemand(DnsConfiguration configuration) throws Exception {
        return new OnDemand(configuration);
    }
}
