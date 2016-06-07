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

import java.util.Comparator;

import org.apache.camel.impl.remote.DefaultServiceCallServer;
import org.xbill.DNS.SRVRecord;

public class DnsServiceCallServer extends DefaultServiceCallServer {
    public static final Comparator<SRVRecord> COMPARATOR = comparator();

    public DnsServiceCallServer(SRVRecord record) {
        super(record.getTarget().toString(true), record.getPort());
    }

    public static Comparator<SRVRecord> comparator() {
        Comparator<SRVRecord> byPriority = (e1, e2) -> Integer.compare(e2.getPriority(), e1.getPriority());
        Comparator<SRVRecord> byWeight = (e1, e2) -> Integer.compare(e2.getWeight(), e1.getWeight());

        return byPriority.thenComparing(byWeight);
    }
}
