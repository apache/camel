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
package org.apache.camel.converter.xmlbeans;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.xmlbeans.XmlObject;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link DataFormat}) using XmlBeans to marshal to and from XML
 */
public class XmlBeansDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private boolean contentTypeHeader = true;

    @Override
    public String getDataFormatName() {
        return "xmlBeans";
    }

    public void marshal(final Exchange exchange, final Object body, final OutputStream stream) throws Exception {
        ObjectHelper.callWithTCCL(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                XmlObject object = ExchangeHelper.convertToMandatoryType(exchange, XmlObject.class, body);
                object.save(stream);
                return null;
            }
        }, exchange);

        if (contentTypeHeader) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            }
        }
    }

    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        return ObjectHelper.callWithTCCL(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return XmlObject.Factory.parse(stream);
            }
        }, exchange);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }


    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then XmlBeans will set the Content-Type header to <tt>application/xml</tt> when marshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

}
