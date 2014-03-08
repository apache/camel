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
package org.apache.camel.component.stax;

import javax.xml.stream.XMLStreamReader;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ExchangeHelper;

/**
 * It uses SAX content handler to handle events.
 * <p/>
 * The {@link Exchange} is expected to contain a message body, that is
 * convertable to both a {@link InputSource} and a {@link XMLStreamReader}
 * to support StAX streaming.
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
        InputSource is = exchange.getIn().getMandatoryBody(InputSource.class);
        XMLStreamReader stream = exchange.getIn().getMandatoryBody(XMLStreamReader.class);
        XMLReader reader = new StaxStreamXMLReader(stream);

        ContentHandler handler;
        if (contentHandlerClass != null) {
            handler = contentHandlerClass.newInstance();
        } else {
            handler = contentHandler;
        }
        reader.setContentHandler(handler);
        reader.parse(is);

        if (ExchangeHelper.isOutCapable(exchange)) {
            // preserve headers
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(handler);
        } else {
            exchange.getIn().setBody(handler);
        }
    }

}
