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
package org.apache.camel.spi;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Service;

/**
 * Represents a <a href="http://camel.apache.org/data-format.html">data format</a> used to marshal objects to and from
 * streams such as Java Serialization or using JAXB2 to encode/decode objects using XML or using SOAP encoding.
 */
public interface DataFormat extends Service {

    /**
     * Marshals the object to the given Stream.
     *
     * @param  exchange  the current exchange
     * @param  graph     the object to be marshalled
     * @param  stream    the output stream to write the marshalled result to
     * @throws Exception can be thrown
     */
    void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception;

    /**
     * Unmarshals the given stream into an object.
     * <p/>
     * <b>Notice:</b> The result is set as body on the exchange OUT message. It is possible to mutate the OUT message
     * provided in the given exchange parameter. For instance adding headers to the OUT message will be preserved.
     * <p/>
     * It's also legal to return the <b>same</b> passed <tt>exchange</tt> as is but also a {@link Message} object as
     * well which will be used as the OUT message of <tt>exchange</tt>.
     *
     * @param  exchange  the current exchange
     * @param  stream    the input stream with the object to be unmarshalled
     * @return           the unmarshalled object
     * @throws Exception can be thrown
     * @see              #unmarshal(Exchange, Object)
     */
    Object unmarshal(Exchange exchange, InputStream stream) throws Exception;

    /**
     * Unmarshals the given body into an object.
     * <p/>
     * <b>Notice:</b> The result is set as body on the exchange OUT message. It is possible to mutate the OUT message
     * provided in the given exchange parameter. For instance adding headers to the OUT message will be preserved.
     * <p/>
     * It's also legal to return the <b>same</b> passed <tt>exchange</tt> as is but also a {@link Message} object as
     * well which will be used as the OUT message of <tt>exchange</tt>.
     * <p/>
     * This method can be used when a dataformat is optimized to handle any kind of message body as-is. For example
     * camel-jaxb has been optimized to do this. The regular {@link #unmarshal(Exchange, InputStream)} method requires
     * Camel to convert the message body into an {@link InputStream} prior to calling the unmarshal method. This can be
     * avoided if the data-format implementation can be optimized to handle this by itself, such as camel-jaxb that can
     * handle message body as a String payload out of the box. When a data format implementation is using this method,
     * then the {@link #unmarshal(Exchange, InputStream)} must also be implemented but should be empty, as Camel will
     * not invoke this method.
     *
     * @param  exchange  the current exchange
     * @param  body      the input object to be unmarshalled
     * @return           the unmarshalled object
     * @throws Exception can be thrown
     */
    default Object unmarshal(Exchange exchange, Object body) throws Exception {
        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
        return unmarshal(exchange, is);
    }
}
