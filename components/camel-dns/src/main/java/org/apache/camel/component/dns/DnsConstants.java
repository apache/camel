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
    public static final String OPERATION_WIKIPEDIA = DnsType.wikipedia.name();

    @Metadata(label = "lookup dig", description = "The DNS class of the lookup. Should match the values of\n" +
                                                  "`org.xbill.dns.DClass`. Optional.",
              javaType = "String")
    public static final String DNS_CLASS = "dns.class";
    @Metadata(label = "lookup", description = "The name to lookup", javaType = "String", required = true)
    public static final String DNS_NAME = "dns.name";
    @Metadata(label = "ip", description = "The domain name.", javaType = "String", required = true)
    public static final String DNS_DOMAIN = "dns.domain";
    @Metadata(label = "dig", description = "The server in particular for the query. If none is given, the default\n" +
                                           "one specified by the OS will be used. Optional.",
              javaType = "String")
    public static final String DNS_SERVER = "dns.server";
    @Metadata(label = "lookup dig", description = "The type of the lookup. Should match the values of `org.xbill.dns.Type`.\n" +
                                                  "Optional.",
              javaType = "String")
    public static final String DNS_TYPE = "dns.type";
    @Metadata(label = "wikipedia", description = "The term", required = true)
    public static final String TERM = "term";

    protected DnsConstants() {
        //Utility class
    }
}
