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
public class DnsConverter {

    /**
     * @param record
     * @return the String representation of a record.
     */
    @Converter
    public String convert(Record record) {
        return record.toString();
    }

    /**
     * @param records
     * @return the String representation of a record.
     */
    @Converter
    public List<String> convert(Record[] records) {
        List<String> list = new ArrayList<String>();
        for (Record rec : records) {
            list.add(convert(rec));
        }
        return list;
    }

    /**
     * @param message
     * @return the String representation of a message.
     */
    @Converter
    public String convert(Message message) {
        return message.toString();
    }

    /**
     * @param address a DNS address
     * @return its String representation.
     */
    @Converter
    public String convert(Address address) {
        return address.toString();
    }

    /**
     * @param address
     * @return the host name of the address.
     */
    @Converter
    public String convert(InetAddress address) {
        return address.getHostAddress();
    }

    /**
     * @param domain
     * @return the InetAddress object for a given domain.
     * @throws UnknownHostException
     */
    @Converter
    public InetAddress convert(String domain) throws UnknownHostException {
        return Address.getByName((String) domain);
    }
}
