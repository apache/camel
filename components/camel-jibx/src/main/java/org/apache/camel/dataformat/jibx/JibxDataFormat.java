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
package org.apache.camel.dataformat.jibx;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.jibx.runtime.BindingDirectory;
import org.jibx.runtime.IBindingFactory;
import org.jibx.runtime.IMarshallingContext;
import org.jibx.runtime.IUnmarshallingContext;
import org.jibx.runtime.JiBXException;

public class JibxDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    public static final String UNMARSHALL_CLASS = "CamelJibxUnmarshallClass";

    private Class<?> unmarshallClass;
    private String bindingName;
    private boolean contentTypeHeader = true;

    public JibxDataFormat() {
    }

    public JibxDataFormat(Class<?> unmarshallClass) {
        this.setUnmarshallClass(unmarshallClass);
    }

    public JibxDataFormat(Class<?> unmarshallClass, String bindingName) {
        this.setUnmarshallClass(unmarshallClass);
        this.setBindingName(bindingName);
    }

    @Override
    public String getDataFormatName() {
        return "jibx";
    }

    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        IBindingFactory bindingFactory = createBindingFactory(body.getClass(), bindingName);
        IMarshallingContext marshallingContext = bindingFactory.createMarshallingContext();
        marshallingContext.marshalDocument(body, null, null, stream);

        if (contentTypeHeader) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            }
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        Class<?> unmarshallType = exchange.getIn().getHeader(UNMARSHALL_CLASS, Class.class);
        if (unmarshallType == null) {
            unmarshallType = getUnmarshallClass();
        }

        ObjectHelper.notNull(unmarshallType, "unmarshallClass or CamelJibxUnmarshallClass header");

        IBindingFactory bindingFactory = createBindingFactory(unmarshallType, bindingName);
        IUnmarshallingContext unmarshallingContext = bindingFactory.createUnmarshallingContext();
        return unmarshallingContext.unmarshalDocument(stream, null);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public Class<?> getUnmarshallClass() {
        return unmarshallClass;
    }

    public void setUnmarshallClass(Class<?> unmarshallClass) {
        this.unmarshallClass = unmarshallClass;
    }

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }


    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then Jibx will set the Content-Type header to <tt>application/xml</tt> when marshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }


    private IBindingFactory createBindingFactory(Class<?> clazz, String bindingName) throws JiBXException {
        if (bindingName == null) {
            return BindingDirectory.getFactory(clazz);
        } else {
            return BindingDirectory.getFactory(bindingName, clazz);
        }
    }
}
