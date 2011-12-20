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
package org.apache.camel.component.dns.types;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Converter;
import org.xbill.DNS.Address;
import org.xbill.DNS.Message;
import org.xbill.DNS.Record;

/**
 * A converter for all the DNS objects used by the DNS component.
 */
@Converter
public final class DnsConverter {

    private DnsConverter() {
    }

    @Converter
    public static String toString(Record record) {
        return record.toString();
    }

    @Converter
    public static List<String> toList(Record[] records) {
        List<String> list = new ArrayList<String>();
        for (Record rec : records) {
            list.add(toString(rec));
        }
        return list;
    }

    @Converter
    public static String toString(Message message) {
        return message.toString();
    }

    @Converter
    public static String toString(Address address) {
        return address.toString();
    }

    @Converter
    public static String toString(InetAddress address) {
        return address.getHostAddress();
    }

    @Converter
    public static InetAddress toInetAddress(String domain) throws UnknownHostException {
        return Address.getByName(domain);
    }

}
