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
package org.apache.camel.component.etcd.processor.remote;

import java.util.Comparator;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.impl.remote.DefaultServiceCallServer;

public class EtcdServiceCallServer extends DefaultServiceCallServer {
    public static final Comparator<EtcdServiceCallServer> COMPARATOR = comparator();

    private final String name;

    @JsonCreator
    public EtcdServiceCallServer(
        @JsonProperty("name") final String name,
        @JsonProperty("address") final String address,
        @JsonProperty("port") final Integer port,
        @JsonProperty("tags") final Map<String, String> tags) {
        super(address, port, tags);

        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Comparator<EtcdServiceCallServer> comparator() {
        Comparator<EtcdServiceCallServer> byAddress = (e1, e2) -> e2.getIp().compareTo(e1.getIp());
        Comparator<EtcdServiceCallServer> byPort = (e1, e2) -> Integer.compare(e2.getPort(), e1.getPort());

        return byAddress.thenComparing(byPort);
    }
}
