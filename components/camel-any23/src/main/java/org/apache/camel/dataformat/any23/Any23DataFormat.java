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
package org.apache.camel.dataformat.any23;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.any23.Any23;
import org.apache.any23.configuration.DefaultConfiguration;
import org.apache.any23.configuration.ModifiableConfiguration;
import org.apache.any23.source.ByteArrayDocumentSource;
import org.apache.any23.writer.TripleHandler;
import org.apache.camel.Exchange;
import org.apache.camel.dataformat.any23.utils.Any23Utils;
import org.apache.camel.dataformat.any23.writer.RDF4JModelWriter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

/**
 * Dataformat for any23 .. This dataformat is intended to convert HTML from a
 * site (or file) into rdf.
 */
@Dataformat("any23")
public class Any23DataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private Any23 any23;

    private Map<String, String> configurations;
    private List<String> extractors;
    private Any23OutputFormat outputFormat;
    private String baseURI;

    public Any23DataFormat() {
    }

    public Any23DataFormat(String baseURI) {
        this.baseURI = baseURI;
    }

    public Any23DataFormat(Any23OutputFormat outputFormat, String baseURI) {
        this.outputFormat = outputFormat;
        this.baseURI = baseURI;
    }

    public Any23DataFormat(Map<String, String> configurations, Any23OutputFormat outputFormat, String baseURI) {
        this.configurations = configurations;
        this.outputFormat = outputFormat;
        this.baseURI = baseURI;
    }

    public Any23DataFormat(Map<String, String> configurations, List<String> extractors, Any23OutputFormat outputFormat, String baseURI) {
        this.configurations = configurations;
        this.extractors = extractors;
        this.outputFormat = outputFormat;
        this.baseURI = baseURI;
    }

    @Override
    public String getDataFormatName() {
        return "any23";
    }

    /**
     * Marshal data. Generate RDF.
     */
    public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
        outputStreamWriter.write("<html><script type=\"application/ld+json\">\n");
        outputStreamWriter.flush();
        Model mdl = (Model)object;
        Rio.write(mdl, outputStream, RDFFormat.JSONLD);
        outputStreamWriter.write("\n</script></html>");
        outputStreamWriter.flush();
        outputStreamWriter.close();
        outputStream.close();
    }

    /**
     * Unmarshal the data
     */
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        ByteArrayDocumentSource source = new ByteArrayDocumentSource(inputStream, this.baseURI, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TripleHandler handler = Any23Utils.obtainHandler(outputFormat, out);
        any23.extract(source, handler);
        handler.close();
        Object respon;
        if (outputFormat == Any23OutputFormat.RDF4JMODEL) {
            respon = ((RDF4JModelWriter)handler).getModel();
        } else {
            respon = out.toString();
        }
        return respon;

    }

    @Override
    protected void doStart() throws Exception {
        ModifiableConfiguration conf = null;
        String[] extrArray = null;
        if (extractors != null && !extractors.isEmpty()) {
            extrArray = new String[extractors.size()];
            extrArray = extractors.toArray(extrArray);
        }
        if (configurations != null && !configurations.isEmpty()) {
            conf = DefaultConfiguration.copy();
            for (Entry<String, String> entry : configurations.entrySet()) {
                conf.setProperty(entry.getKey(), entry.getValue());
            }
        }
        if (outputFormat == null) {
            // Default output format
            outputFormat = Any23OutputFormat.RDF4JMODEL;
        }
        if (conf == null && extrArray == null) {
            any23 = new Any23();
        } else if (conf != null && extrArray == null) {
            any23 = new Any23(conf);
        } else if (conf == null && extrArray != null) {
            any23 = new Any23(extrArray);
        } else if (conf != null && extrArray != null) {
            any23 = new Any23(conf, extrArray);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public Any23 getAny23() {
        return any23;
    }

    public Any23DataFormat setAny23(Any23 any23) {
        this.any23 = any23;
        return this;
    }

    public Map<String, String> getConfigurations() {
        return configurations;
    }

    public Any23DataFormat setConfigurations(Map<String, String> configurations) {
        this.configurations = configurations;
        return this;
    }

    public List<String> getExtractors() {
        return extractors;
    }

    public Any23DataFormat setExtractors(List<String> extractors) {
        this.extractors = extractors;
        return this;
    }

    public Any23OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public Any23DataFormat setOutputFormat(Any23OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
        return this;
    }

    public String getBaseURI() {
        return baseURI;
    }

    public Any23DataFormat setBaseURI(String baseURI) {
        this.baseURI = baseURI;
        return this;
    }

}
