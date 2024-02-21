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
package org.apache.camel.dataformat.beanio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.beanio.BeanReader;
import org.beanio.BeanReaderErrorHandler;
import org.beanio.BeanWriter;
import org.beanio.StreamFactory;
import org.beanio.Unmarshaller;

import static org.apache.camel.dataformat.beanio.BeanIOHelper.getOrCreateBeanReaderErrorHandler;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ( {@link DataFormat}) for beanio data.
 */
@Dataformat("beanio")
public class BeanIODataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private transient CamelContext camelContext;
    private transient StreamFactory factory;
    private transient BeanIOConfiguration configuration;

    private String streamName;
    private String mapping;
    private boolean ignoreUnidentifiedRecords;
    private boolean ignoreUnexpectedRecords;
    private boolean ignoreInvalidRecords;
    private Charset encoding = Charset.defaultCharset();
    private Properties properties;
    private BeanReaderErrorHandler beanReaderErrorHandler;
    private String beanReaderErrorHandlerType;
    private boolean unmarshalSingleObject;

    public BeanIODataFormat() {
    }

    public BeanIODataFormat(String mapping, String streamName) {
        setMapping(mapping);
        setStreamName(streamName);
    }

    @Override
    public String getDataFormatName() {
        return "beanio";
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        org.apache.camel.util.ObjectHelper.notNull(getStreamName(), "Stream name not configured.");
        if (factory == null) {
            // Create the stream factory that will be used to read/write objects.
            factory = StreamFactory.newInstance();
            if (ResourceHelper.isClasspathUri(getMapping())) {
                loadMappingResource();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!ResourceHelper.isClasspathUri(getMapping())) {
            loadMappingResource();
        }

        configuration = new BeanIOConfiguration();
        configuration.setStreamName(streamName);
        configuration.setMapping(mapping);
        configuration.setIgnoreUnidentifiedRecords(ignoreUnidentifiedRecords);
        configuration.setIgnoreUnexpectedRecords(ignoreUnexpectedRecords);
        configuration.setIgnoreInvalidRecords(ignoreInvalidRecords);
        configuration.setEncoding(encoding);
        configuration.setProperties(properties);
        configuration.setBeanReaderErrorHandler(beanReaderErrorHandler);
        configuration.setBeanReaderErrorHandlerType(beanReaderErrorHandlerType);
        configuration.setUnmarshalSingleObject(unmarshalSingleObject);
    }

    @Override
    protected void doStop() throws Exception {
        factory = null;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    StreamFactory getFactory() {
        return factory;
    }

    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        List<Object> models = getModels(exchange, body);
        writeModels(stream, models);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        if (isUnmarshalSingleObject()) {
            return readSingleModel(exchange, stream);
        } else {
            return readModels(exchange, stream);
        }
    }

    private void loadMappingResource() throws Exception {
        // Load the mapping file using the resource helper to ensure it can be loaded in OSGi and other environments
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), getMapping());
        try {
            if (getProperties() != null) {
                factory.load(is, getProperties());
            } else {
                factory.load(is);
            }
        } finally {
            IOHelper.close(is);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getModels(Exchange exchange, Object body) {
        List<Object> models;

        if (body instanceof Map && isUnmarshalSingleObject()) {
            models = new ArrayList<>();
            models.add(body);
        } else if ((models = exchange.getContext().getTypeConverter().convertTo(List.class, body)) == null) {
            models = new ArrayList<>();
            for (Object model : ObjectHelper.createIterable(body)) {
                models.add(model);
            }
        }
        return models;
    }

    private void writeModels(OutputStream stream, List<Object> models) {
        BufferedWriter streamWriter = IOHelper.buffered(new OutputStreamWriter(stream, getEncoding()));
        BeanWriter out = factory.createWriter(getStreamName(), streamWriter);

        for (Object obj : models) {
            out.write(obj);
        }

        out.flush();
        out.close();
    }

    private List<Object> readModels(Exchange exchange, InputStream stream) throws Exception {
        List<Object> results = new ArrayList<>();
        BufferedReader streamReader = IOHelper.buffered(new InputStreamReader(stream, getEncoding()));

        BeanReader in = factory.createReader(getStreamName(), streamReader);

        BeanReaderErrorHandler errorHandler = getOrCreateBeanReaderErrorHandler(configuration, exchange, results, null);
        in.setErrorHandler(errorHandler);

        try {
            Object readObject;
            while ((readObject = in.read()) != null) {
                if (readObject instanceof BeanIOHeader) {
                    exchange.getOut().getHeaders().putAll(((BeanIOHeader) readObject).getHeaders());
                }
                results.add(readObject);
            }
        } finally {
            in.close();
        }

        return results;
    }

    private Object readSingleModel(Exchange exchange, InputStream stream) throws NoTypeConversionAvailableException {
        BufferedReader streamReader = IOHelper.buffered(new InputStreamReader(stream, getEncoding()));
        try {
            String data = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, streamReader);
            Unmarshaller unmarshaller = factory.createUnmarshaller(getStreamName());
            return unmarshaller.unmarshal(data);
        } finally {
            IOHelper.close(stream);
        }
    }

    public void setFactory(StreamFactory factory) {
        this.factory = factory;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public boolean isIgnoreUnidentifiedRecords() {
        return ignoreUnidentifiedRecords;
    }

    public void setIgnoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
        this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
    }

    public boolean isIgnoreUnexpectedRecords() {
        return ignoreUnexpectedRecords;
    }

    public void setIgnoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
        this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
    }

    public boolean isIgnoreInvalidRecords() {
        return ignoreInvalidRecords;
    }

    public void setIgnoreInvalidRecords(boolean ignoreInvalidRecords) {
        this.ignoreInvalidRecords = ignoreInvalidRecords;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public BeanReaderErrorHandler getBeanReaderErrorHandler() {
        return beanReaderErrorHandler;
    }

    public void setBeanReaderErrorHandler(BeanReaderErrorHandler beanReaderErrorHandler) {
        this.beanReaderErrorHandler = beanReaderErrorHandler;
    }

    public String getBeanReaderErrorHandlerType() {
        return beanReaderErrorHandlerType;
    }

    public void setBeanReaderErrorHandlerType(String beanReaderErrorHandlerType) {
        this.beanReaderErrorHandlerType = beanReaderErrorHandlerType;
    }

    public void setBeanReaderErrorHandlerType(Class<?> beanReaderErrorHandlerType) {
        this.beanReaderErrorHandlerType = beanReaderErrorHandlerType.getName();
    }

    public boolean isUnmarshalSingleObject() {
        return unmarshalSingleObject;
    }

    public void setUnmarshalSingleObject(boolean unmarshalSingleObject) {
        this.unmarshalSingleObject = unmarshalSingleObject;
    }
}
