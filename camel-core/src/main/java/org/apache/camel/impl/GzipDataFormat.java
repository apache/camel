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
package org.apache.camel.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.util.IOHelper;

/**
 * GZip {@link org.apache.camel.spi.DataFormat} for reading/writing data using gzip.
 */
public class GzipDataFormat extends org.apache.camel.support.ServiceSupport implements DataFormat, DataFormatName {

    @Override
    public String getDataFormatName() {
        return "gzip";
    }

    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);

        GZIPOutputStream zipOutput = new GZIPOutputStream(stream);
        try {
            IOHelper.copy(is, zipOutput);
        } finally {
            // must close all input streams
            IOHelper.close(is, zipOutput);
        }
    }

    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        GZIPInputStream unzipInput = null;

        OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);
        try {
            unzipInput = new GZIPInputStream(inputStream);
            IOHelper.copy(unzipInput, osb);
            return osb.build();
        } finally {
            // must close all input streams
            IOHelper.close(osb, unzipInput, inputStream);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
