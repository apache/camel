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
package org.apache.camel.component.stax;

import javax.xml.stream.XMLStreamReader;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;

import com.ctc.wstx.sr.ValidatingStreamReader;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ExchangeHelper;

/**
 * It uses SAX content handler to handle events.
 * <p/>
 * The {@link Exchange} is expected to contain a message body, that is convertable to both a {@link InputSource} and a
 * {@link XMLStreamReader} to support StAX streaming.
 */
public class StAXProcessor implements Processor {
    private final Class<ContentHandler> contentHandlerClass;
    private final ContentHandler contentHandler;

    public StAXProcessor(Class<ContentHandler> contentHandlerClass) {
        this.contentHandlerClass = contentHandlerClass;
        this.contentHandler = null;
    }

    public StAXProcessor(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
        this.contentHandlerClass = null;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        XMLStreamReader stream = null;
        try {
            stream = exchange.getIn().getMandatoryBody(XMLStreamReader.class);
            StaxStreamXMLReader reader = new StaxStreamXMLReader(stream);
            ContentHandler handler;
            if (this.contentHandlerClass != null) {
                handler = this.contentHandlerClass.getDeclaredConstructor().newInstance();
            } else {
                handler = this.contentHandler;
            }

            reader.setContentHandler(handler);
            // InputSource is ignored anyway
            reader.parse((InputSource) null);
            if (ExchangeHelper.isOutCapable(exchange)) {
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                exchange.getOut().setBody(handler);
            } else {
                exchange.getIn().setBody(handler);
            }
        } finally {
            if (stream != null) {
                stream.close();
                if (stream instanceof ValidatingStreamReader) {
                    // didn't find any method without using the woodstox package
                    ((ValidatingStreamReader) stream).closeCompletely();
                }
            }
        }
    }

}
