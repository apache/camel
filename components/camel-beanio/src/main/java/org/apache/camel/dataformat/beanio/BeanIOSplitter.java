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
package org.apache.camel.dataformat.beanio;

import java.io.File;
import java.io.Reader;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.beanio.BeanReader;
import org.beanio.StreamFactory;

public class BeanIOSplitter implements Expression {

    // TODO: should not reuse dataformat but have its own configuration likely
    // TODO: need some way of starting to load the stream factory
    private BeanIODataFormat dataFormat;

    public BeanIOSplitter(BeanIODataFormat dataFormat) throws Exception {
        this.dataFormat = dataFormat;
        ServiceHelper.startService(dataFormat);
    }

    public Object evaluate(Exchange exchange) throws InvalidPayloadException {
        Message msg = exchange.getIn();
        Object body = msg.getBody();

        StreamFactory sf = dataFormat.getFactory();

        BeanReader beanReader = null;
        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }
        if (body instanceof File) {
            File file = (File) body;
            beanReader = sf.createReader(dataFormat.getStreamName(), file);
        }
        if (beanReader == null) {
            Reader reader = msg.getMandatoryBody(Reader.class);
            beanReader = sf.createReader(dataFormat.getStreamName(), reader);
        }

        return new BeanIOIterator(beanReader);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        try {
            Object result = evaluate(exchange);
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
        } catch (InvalidPayloadException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
