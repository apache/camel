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
package org.apache.camel.component.dns;

import org.apache.camel.spi.Metadata;

public class DnsConstants {

    public static final String OPERATION_DIG = DnsType.dig.name();
    public static final String OPERATION_IP = DnsType.ip.name();
    public static final String OPERATION_LOOKUP = DnsType.lookup.name();
    /**
     * @deprecated the Wikipedia over DNS service is no longer available
     */
    @Deprecated
    public static final String OPERATION_WIKIPEDIA = DnsType.wikipedia.name();

    @Metadata(label = "lookup dig",
              description = "The DNS class to query, as a dnsjava DClass mnemonic such as IN (the default), CH"
                            + " or HS. The lookup operation fails on an unknown class, while dig uses"
                            + " IN instead.",
              javaType = "String")
    public static final String DNS_CLASS = "CamelDnsClass";
    @Metadata(label = "lookup dig",
              description = "The domain name to query. For lookup, a name without a trailing dot can also be tried"
                            + " with the search domains of the system, while dig always queries the"
                            + " name as an absolute name.",
              javaType = "String", required = true)
    public static final String DNS_NAME = "CamelDnsName";
    @Metadata(label = "ip", description = "The host name to resolve to an IP address, with the resolver of the JVM"
                                          + " (InetAddress.getByName). An IP address literal is returned as is.",
              javaType = "String", required = true)
    public static final String DNS_DOMAIN = "CamelDnsDomain";
    @Metadata(label = "dig", description = "The host name or IP address of the DNS server to send the query to, on port 53."
                                           + " If not set, the first DNS server configured in the operating system is"
                                           + " used.",
              javaType = "String")
    public static final String DNS_SERVER = "CamelDnsServer";
    @Metadata(label = "lookup dig",
              description = "The record type to query, as a dnsjava Type mnemonic such as A (the default), AAAA,"
                            + " MX or TXT. The lookup operation fails on an unknown type, while dig"
                            + " uses A instead.",
              javaType = "String")
    public static final String DNS_TYPE = "CamelDnsType";
    /**
     * @deprecated the Wikipedia over DNS service is no longer available
     */
    @Deprecated
    @Metadata(label = "wikipedia", description = "The search term, sent as a TXT query for the term followed by .wp.dg.cx.",
              required = true,
              deprecationNote = "The Wikipedia over DNS service is no longer available")
    public static final String TERM = "CamelDnsTerm";

    protected DnsConstants() {
        //Utility class
    }
}
