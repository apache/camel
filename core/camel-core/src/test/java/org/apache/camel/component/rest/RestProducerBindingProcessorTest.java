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
package org.apache.camel.component.rest;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RestProducerBindingProcessorTest {

    public static class RequestPojo {
    }

    public static class ResponsePojo {
    }

    final AsyncCallback callback = mock(AsyncCallback.class);

    final CamelContext context = new DefaultCamelContext();
    final DataFormat jsonDataFormat = mock(DataFormat.class);
    final DataFormat outJsonDataFormat = mock(DataFormat.class);
    final DataFormat outXmlDataFormat = mock(DataFormat.class);
    final AsyncProcessor processor = mock(AsyncProcessor.class);
    final DataFormat xmlDataFormat = mock(DataFormat.class);

    @Test
    public void shouldMarshalAndUnmarshalJson() throws Exception {
        final String outType = ResponsePojo.class.getName();

        final RestProducerBindingProcessor bindingProcessor = new RestProducerBindingProcessor(processor, context, jsonDataFormat, xmlDataFormat, outJsonDataFormat,
                                                                                               outXmlDataFormat, "json", true, outType);

        final Exchange exchange = new DefaultExchange(context);
        final Message input = new DefaultMessage(context);

        final RequestPojo request = new RequestPojo();
        input.setBody(request);
        exchange.setIn(input);

        final ResponsePojo response = new ResponsePojo();
        when(outJsonDataFormat.unmarshal(same(exchange), any(InputStream.class))).thenReturn(response);

        final ArgumentCaptor<AsyncCallback> bindingCallback = ArgumentCaptor.forClass(AsyncCallback.class);

        when(processor.process(same(exchange), bindingCallback.capture())).thenReturn(false);

        bindingProcessor.process(exchange, callback);

        verify(jsonDataFormat).marshal(same(exchange), same(request), any(OutputStream.class));

        assertNotNull(bindingCallback.getValue());

        final AsyncCallback that = bindingCallback.getValue();

        that.done(false);

        Assert.assertSame(response, exchange.getMessage().getBody());
    }

    @Test
    public void shouldMarshalAndUnmarshalXml() throws Exception {
        final String outType = ResponsePojo.class.getName();

        final RestProducerBindingProcessor bindingProcessor = new RestProducerBindingProcessor(processor, context, jsonDataFormat, xmlDataFormat, outJsonDataFormat,
                                                                                               outXmlDataFormat, "xml", true, outType);

        final Exchange exchange = new DefaultExchange(context);
        final Message input = new DefaultMessage(context);

        final RequestPojo request = new RequestPojo();
        input.setBody(request);
        exchange.setIn(input);

        final ResponsePojo response = new ResponsePojo();
        when(outXmlDataFormat.unmarshal(same(exchange), any(InputStream.class))).thenReturn(response);

        final ArgumentCaptor<AsyncCallback> bindingCallback = ArgumentCaptor.forClass(AsyncCallback.class);

        when(processor.process(same(exchange), bindingCallback.capture())).thenReturn(false);

        bindingProcessor.process(exchange, callback);

        verify(xmlDataFormat).marshal(same(exchange), same(request), any(OutputStream.class));

        assertNotNull(bindingCallback.getValue());

        final AsyncCallback that = bindingCallback.getValue();

        that.done(false);

        Assert.assertSame(response, exchange.getMessage().getBody());
    }

    @Test
    public void shouldNotMarshalAndUnmarshalByDefault() throws Exception {
        final String outType = ResponsePojo.class.getName();

        final RestProducerBindingProcessor bindingProcessor = new RestProducerBindingProcessor(processor, context, jsonDataFormat, xmlDataFormat, outJsonDataFormat,
                                                                                               outXmlDataFormat, "off", true, outType);

        final Exchange exchange = new DefaultExchange(context);
        final Message input = new DefaultMessage(context);

        final RequestPojo request = new RequestPojo();
        input.setBody(request);
        exchange.setIn(input);

        final ArgumentCaptor<AsyncCallback> bindingCallback = ArgumentCaptor.forClass(AsyncCallback.class);

        when(processor.process(same(exchange), bindingCallback.capture())).thenReturn(false);

        bindingProcessor.process(exchange, callback);

        assertNotNull(bindingCallback.getValue());

        final AsyncCallback that = bindingCallback.getValue();

        that.done(false);
    }
}
