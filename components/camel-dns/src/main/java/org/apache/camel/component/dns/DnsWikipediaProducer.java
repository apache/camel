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
package org.apache.camel.component.dns;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

/**
 * an endpoint to make queries against wikipedia using
 * the short TXT query.
 * <p/>
 * See here for a reference:
 * http://www.commandlinefu.com/commands/view/2829/query-wikipedia-via-console-over-dns
 * <p/>
 * This endpoint accepts the following header:
 * term: a simple term to use to query wikipedia.
 */
public class DnsWikipediaProducer extends DefaultProducer {

    public DnsWikipediaProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SimpleResolver resolver = new SimpleResolver();
        int type = Type.TXT;
        Name name = Name.fromString(String.valueOf(exchange.getIn().getHeader(DnsConstants.TERM)) + ".wp.dg.cx", Name.root);
        Record rec = Record.newRecord(name, type, DClass.IN);
        Message query = Message.newQuery(rec);
        Message response = resolver.send(query);
        Record[] records = response.getSectionArray(Section.ANSWER);
        if (records.length > 0) {
            exchange.getIn().setBody(records[0].rdataToString());
        } else {
            exchange.getIn().setBody(null);
        }
    }
}
