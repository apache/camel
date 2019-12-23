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
package org.apache.camel.component.xslt.saxon;

import javax.xml.transform.Source;
import javax.xml.transform.stax.StAXSource;

import org.apache.camel.Exchange;
import org.apache.camel.component.xslt.XmlSourceHandlerFactoryImpl;

public class SaxonXmlSourceHandlerFactoryImpl extends XmlSourceHandlerFactoryImpl {

    @Override
    protected Source getSource(Exchange exchange, Object body) {
        // body may already be a source
        if (body instanceof Source) {
            return (Source) body;
        }
        Source source = null;
        if (body != null) {
            // try StAX if enabled
            source = exchange.getContext().getTypeConverter().tryConvertTo(StAXSource.class, exchange, body);
        }
        if (source == null) {
            source = super.getSource(exchange, body);
        }
        return source;
    }
}
