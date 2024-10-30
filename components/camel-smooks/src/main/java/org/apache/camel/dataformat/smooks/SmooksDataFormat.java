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
package org.apache.camel.dataformat.smooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.smooks.SmooksComponent;
import org.apache.camel.component.smooks.SmooksProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.processor.MarshalProcessor;
import org.apache.camel.support.processor.UnmarshalProcessor;
import org.apache.camel.support.service.ServiceSupport;
import org.smooks.Smooks;
import org.smooks.SmooksFactory;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.api.io.Sink;
import org.smooks.engine.lookup.ExportsLookup;
import org.smooks.io.payload.Exports;
import org.smooks.io.sink.StringSink;
import org.smooks.io.source.JavaSource;
import org.smooks.io.source.StreamSource;

/**
 * SmooksDataFormat is a Camel data format which is a pluggable transformer capable of transforming from one dataformat
 * to another and back again. This means that what is marshaled can be unmarshalled by an instance of this class.
 * <p/>
 * <p/>
 * A smooks configuration for a SmooksDataFormat should not utilize Smooks features such as routing that might allocate
 * system resources. The reason for this is that there is no functionality in the SmooksDataFormat which will close
 * those resources. If you need to use these Smooks features please take a look at the {@link SmooksComponent} or
 * {@link SmooksProcessor} as they hook into Camels lifecycle management and will close resources correctly.
 * <p/>
 */
@Dataformat("smooks")
public class SmooksDataFormat extends ServiceSupport implements DataFormat, CamelContextAware {
    private Smooks smooks;
    private CamelContext camelContext;
    private String smooksConfig;

    /**
     * Marshals the Object 'fromBody' to an OutputStream 'toStream'
     * </p>
     * <p/>
     * The Camel framework will call this method from {@link MarshalProcessor#process(Exchange)} and it will take care
     * of setting the Out Message's body to the bytes written to the toStream OutputStream.
     *
     * @param exchange The Camel {@link Exchange}.
     * @param fromBody The object to be marshalled into the output stream.
     * @param toStream The output stream that will be written to.
     */
    @Override
    public void marshal(final Exchange exchange, final Object fromBody, final OutputStream toStream) throws Exception {
        final ExecutionContext executionContext = smooks.createExecutionContext();
        final TypeConverter typeConverter = exchange.getContext().getTypeConverter();
        final JavaSource javaSource = typeConverter.mandatoryConvertTo(JavaSource.class, exchange, fromBody);
        final StringSink stringSink = new StringSink();
        smooks.filterSource(executionContext, javaSource, stringSink);

        toStream.write(stringSink.getResult().getBytes(executionContext.getContentEncoding()));
    }

    /**
     * Unmarshals the fromStream to an Object.
     * </p>
     * The Camel framework will call this method from {@link UnmarshalProcessor#process(Exchange)} and it will take care
     * of setting the returned Object on the Out Message's body.
     *
     * @param exchange   The Camel {@link Exchange}.
     * @param fromStream The InputStream that will be unmarshalled into an Object instance.
     */
    @Override
    public Object unmarshal(final Exchange exchange, final InputStream fromStream) {
        final ExecutionContext executionContext = smooks.createExecutionContext();
        final Exports exports = smooks.getApplicationContext().getRegistry().lookup(new ExportsLookup());
        final Sink[] sinks = exports.createSinks();
        smooks.filterSource(executionContext, new StreamSource<>(fromStream), sinks);
        return getResult(exports, sinks, exchange);
    }

    protected Object getResult(final Exports exports, final Sink[] sinks, final Exchange exchange) {
        final List<Object> objects = Exports.extractSinks(sinks, exports);
        if (objects.size() == 1) {
            return objects.get(0);
        } else {
            return objects;
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void doStart() {
        final SmooksFactory smooksFactory
                = (SmooksFactory) camelContext.getRegistry().lookupByName(SmooksFactory.class.getName());
        try {
            if (smooksFactory != null) {
                smooks = smooksFactory.createInstance(smooksConfig);
            } else {
                smooks = new Smooks(smooksConfig);
            }
        } catch (IOException | SAXException e) {
            throw new SmooksException(e.getMessage(), e);
        }
    }

    @Override
    public void doStop() {
        if (smooks != null) {
            smooks.close();
        }
    }

    public String getSmooksConfig() {
        return smooksConfig;
    }

    public void setSmooksConfig(String smooksConfig) {
        this.smooksConfig = smooksConfig;
    }
}
