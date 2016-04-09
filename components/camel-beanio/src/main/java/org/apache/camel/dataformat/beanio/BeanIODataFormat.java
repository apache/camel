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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.beanio.BeanReader;
import org.beanio.BeanWriter;
import org.beanio.StreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) for beanio data.
 */
public class BeanIODataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(BeanIODataFormat.class);

    private transient CamelContext camelContext;
    private transient StreamFactory factory;
    private BeanIOConfiguration configuration = new BeanIOConfiguration();

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
    protected void doStart() throws Exception {
        ObjectHelper.notNull(getStreamName(), "Stream name not configured.");
        if (factory == null) {
            // Create the stream factory that will be used to read/write objects.
            factory = StreamFactory.newInstance();

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
    }

    @Override
    protected void doStop() throws Exception {
        factory = null;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    StreamFactory getFactory() {
        return factory;
    }

    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        List<Object> models = getModels(exchange, body);
        writeModels(stream, models);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return readModels(exchange, stream);
    }

    @SuppressWarnings("unchecked")
    private List<Object> getModels(Exchange exchange, Object body) {
        List<Object> models;
        if ((models = exchange.getContext().getTypeConverter().convertTo(List.class, body)) == null) {
            models = new ArrayList<Object>();
            Iterator<Object> it = ObjectHelper.createIterator(body);
            while (it.hasNext()) {
                models.add(it.next());
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

    private List<Object> readModels(Exchange exchange, InputStream stream) {
        List<Object> results = new ArrayList<Object>();
        BufferedReader streamReader = IOHelper.buffered(new InputStreamReader(stream, getEncoding()));

        BeanReader in = factory.createReader(getStreamName(), streamReader);

        try {
            in.setErrorHandler(new BeanIOErrorHandler(configuration));

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

    public String getMapping() {
        return configuration.getMapping();
    }

    public void setIgnoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
        configuration.setIgnoreUnexpectedRecords(ignoreUnexpectedRecords);
    }

    public void setProperties(Properties properties) {
        configuration.setProperties(properties);
    }

    public void setStreamName(String streamName) {
        configuration.setStreamName(streamName);
    }

    public boolean isIgnoreUnidentifiedRecords() {
        return configuration.isIgnoreUnidentifiedRecords();
    }

    public boolean isIgnoreInvalidRecords() {
        return configuration.isIgnoreInvalidRecords();
    }

    public void setIgnoreInvalidRecords(boolean ignoreInvalidRecords) {
        configuration.setIgnoreInvalidRecords(ignoreInvalidRecords);
    }

    public void setEncoding(Charset encoding) {
        configuration.setEncoding(encoding);
    }

    public boolean isIgnoreUnexpectedRecords() {
        return configuration.isIgnoreUnexpectedRecords();
    }

    public Properties getProperties() {
        return configuration.getProperties();
    }

    public String getStreamName() {
        return configuration.getStreamName();
    }

    public void setMapping(String mapping) {
        configuration.setMapping(mapping);
    }

    public void setIgnoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
        configuration.setIgnoreUnidentifiedRecords(ignoreUnidentifiedRecords);
    }

    public Charset getEncoding() {
        return configuration.getEncoding();
    }
}
