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
package org.apache.camel.builder.xml;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

import org.apache.camel.Exchange;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * {@link ErrorHandler} and {@link ErrorListener} which will ignore warnings,
 * and throws error and fatal as exception, which ensures those can be caught by Camel and dealt-with.
 * <p/>
 * Also any warning, error or fatal error is stored on the {@link Exchange} as a property with the keys
 * {@link Exchange#XSLT_WARNING}, {@link Exchange#XSLT_ERROR}, and {@link Exchange#XSLT_FATAL_ERROR} which
 * allows end users to access those information form the exchange.
 */
public class DefaultTransformErrorHandler implements ErrorHandler, ErrorListener {

    private final Exchange exchange;

    public DefaultTransformErrorHandler(Exchange exchange) {
        this.exchange = exchange;
    }

    public void error(SAXParseException exception) throws SAXException {
        exchange.setProperty(Exchange.XSLT_ERROR, exception);
        throw exception;
    }

    public void fatalError(SAXParseException exception) throws SAXException {
        exchange.setProperty(Exchange.XSLT_FATAL_ERROR, exception);
        throw exception;
    }

    public void warning(SAXParseException exception) throws SAXException {
        exchange.setProperty(Exchange.XSLT_WARNING, exception);
    }

    public void error(TransformerException exception) throws TransformerException {
        exchange.setProperty(Exchange.XSLT_ERROR, exception);
        throw exception;
    }

    public void fatalError(TransformerException exception) throws TransformerException {
        exchange.setProperty(Exchange.XSLT_FATAL_ERROR, exception);
        throw exception;
    }

    public void warning(TransformerException exception) throws TransformerException {
        exchange.setProperty(Exchange.XSLT_WARNING, exception);
    }

}
