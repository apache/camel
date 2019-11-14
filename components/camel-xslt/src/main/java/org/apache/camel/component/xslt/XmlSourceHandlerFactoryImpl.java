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
package org.apache.camel.component.xslt;

import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.RuntimeTransformException;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.builder.xml.XMLConverterHelper;

/**
 * Handler for xml sources
 */
public class XmlSourceHandlerFactoryImpl implements SourceHandlerFactory {

    private XMLConverterHelper converter = new XMLConverterHelper();
    private boolean isFailOnNullBody = true;

    /**
     * Returns true if we fail when the body is null.
     */
    public boolean isFailOnNullBody() {
        return isFailOnNullBody;
    }

    /**
     * Set if we should fail when the body is null
     */
    public void setFailOnNullBody(boolean failOnNullBody) {
        isFailOnNullBody = failOnNullBody;
    }

    @Override
    public Source getSource(Exchange exchange) throws Exception {
        // only convert to input stream if really needed
        if (isInputStreamNeeded(exchange)) {
            InputStream is = exchange.getIn().getBody(InputStream.class);
            return getSource(exchange, is);
        } else {
            Object body = exchange.getIn().getBody();
            return getSource(exchange, body);
        }
    }

    /**
     * Checks whether we need an {@link InputStream} to access the message body.
     * <p/>
     * Depending on the content in the message body, we may not need to convert
     * to {@link InputStream}.
     *
     * @param exchange the current exchange
     * @return <tt>true</tt> to convert to {@link InputStream} beforehand converting to {@link Source} afterwards.
     */
    protected boolean isInputStreamNeeded(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return false;
        }

        if (body instanceof InputStream) {
            return true;
        } else if (body instanceof Source) {
            return false;
        } else if (body instanceof String) {
            return false;
        } else if (body instanceof byte[]) {
            return false;
        } else if (body instanceof Node) {
            return false;
        } else if (exchange.getContext().getTypeConverterRegistry().lookup(Source.class, body.getClass()) != null) {
            //there is a direct and hopefully optimized converter to Source
            return false;
        }
        // yes an input stream is needed
        return true;
    }

    /**
     * Converts the inbound body to a {@link Source}, if the body is <b>not</b> already a {@link Source}.
     * <p/>
     * This implementation will prefer to source in the following order:
     * <ul>
     *   <li>StAX - If StAX is allowed</li>
     *   <li>SAX - SAX as 2nd choice</li>
     *   <li>Stream - Stream as 3rd choice</li>
     *   <li>DOM - DOM as 4th choice</li>
     * </ul>
     */
    protected Source getSource(Exchange exchange, Object body) {
        // body may already be a source
        if (body instanceof Source) {
            return (Source) body;
        }
        Source source = null;
        if (body != null) {
            // then try SAX
            source = exchange.getContext().getTypeConverter().tryConvertTo(SAXSource.class, exchange, body);
            if (source == null) {
                // then try stream
                source = exchange.getContext().getTypeConverter().tryConvertTo(StreamSource.class, exchange, body);
            }
            if (source == null) {
                // and fallback to DOM
                source = exchange.getContext().getTypeConverter().tryConvertTo(DOMSource.class, exchange, body);
            }
            // as the TypeConverterRegistry will look up source the converter differently if the type converter is loaded different
            // now we just put the call of source converter at last
            if (source == null) {
                TypeConverter tc = exchange.getContext().getTypeConverterRegistry().lookup(Source.class, body.getClass());
                if (tc != null) {
                    source = tc.convertTo(Source.class, exchange, body);
                }
            }
        }

        if (source == null) {
            if (isFailOnNullBody()) {
                throw new ExpectedBodyTypeException(exchange, Source.class);
            } else {
                try {
                    source = converter.toDOMSource(converter.createDocument());
                } catch (ParserConfigurationException | TransformerException e) {
                    throw new RuntimeTransformException(e);
                }
            }
        }

        return source;
    }
}
