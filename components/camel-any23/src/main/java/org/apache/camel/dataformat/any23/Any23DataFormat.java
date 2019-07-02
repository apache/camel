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

import java.io.InputStream;
import java.io.OutputStream;
import org.apache.any23.Any23;
import org.apache.any23.configuration.DefaultConfiguration;
import org.apache.any23.configuration.ModifiableConfiguration;
import org.apache.any23.source.DocumentSource;
import org.apache.any23.source.StringDocumentSource;
import org.apache.any23.writer.JSONLDWriter;
import org.apache.any23.writer.NQuadsWriter;
import org.apache.any23.writer.NTriplesWriter;
import org.apache.any23.writer.RDFXMLWriter;
import org.apache.any23.writer.TripleHandler;
import org.apache.any23.writer.TurtleWriter;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dataformat for any23 .. This dataformat is intended to convert HTML from a
 * site (or file) into rdf.
 */
@Dataformat("any23")
public class Any23DataFormat extends ServiceSupport implements DataFormat, DataFormatName {

  /*
     * Our Logger
   */
  private static final Logger LOG = LoggerFactory.getLogger(Any23DataFormat.class);

  private Any23 any23;
  private Any23OutputFormat format = Any23OutputFormat.RDFXML;
  private ModifiableConfiguration conf;
  private String[] extractorsList;

  private String configurations;
  private String extractors;
  private String outputFormat;
  private String documentIRI = "http://mock.foo/bar";

  @Override
  public String getDataFormatName() {
    return "any23";
  }

  /**
   * Marshal data. Generate RDF.
   */
  public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
    final String payload = ExchangeHelper.convertToMandatoryType(exchange, String.class, object);
    DocumentSource source = new StringDocumentSource(payload, documentIRI);
    TripleHandler handler;
    switch (format) {
      case NTRIPLES:
        handler = new NTriplesWriter(outputStream);
        break;
      case TURTLE:
        handler = new TurtleWriter(outputStream);
        break;
      case NQUADS:
        handler = new NQuadsWriter(outputStream);
        break;
      case RDFXML:
        handler = new RDFXMLWriter(outputStream);
        break;
      case JSONLD:
        handler = new JSONLDWriter(outputStream);
        break;
      case MODEL:
        handler = new NTriplesWriter(outputStream);
        break;
      default:
        handler = new NTriplesWriter(outputStream);
    }
    any23.extract(source, handler);
    handler.close();
  }

  /**
   * Unmarshal the data
   */
  public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
    //TODO
    return null;
  }

  @Override
  protected void doStart() throws Exception {
    conf = DefaultConfiguration.copy();
    if (configurations != null) {
      String[] newConfigs = configurations.split(";");
      for (String con : newConfigs) {
        String[] vals = con.split("=");
        conf.setProperty(vals[0], vals[0]);
      }
    }
    if (extractors != null) {
      extractorsList = extractors.split(";");
    }
    if (configurations == null && extractors == null) {
      any23 = new Any23();
    } else if (configurations != null && extractors == null) {
      any23 = new Any23(conf);
    } else if (configurations == null && extractors != null) {
      any23 = new Any23(extractors);
    } else if (configurations != null && extractors != null) {
      any23 = new Any23(conf, extractors);
    }
    if (outputFormat != null) {
      format = Any23OutputFormat.valueOf(outputFormat);
    }
  }

  @Override
  protected void doStop() throws Exception {
    // noop
  }

  public Any23 getAny23() {
    return any23;
  }

  public void setAny23(Any23 any23) {
    this.any23 = any23;
  }

  public Any23OutputFormat getFormat() {
    return format;
  }

  public void setFormat(Any23OutputFormat format) {
    this.format = format;
  }

  public ModifiableConfiguration getConf() {
    return conf;
  }

  public void setConf(ModifiableConfiguration conf) {
    this.conf = conf;
  }

  public String[] getExtractorsList() {
    return extractorsList;
  }

  public void setExtractorsList(String[] extractorsList) {
    this.extractorsList = extractorsList;
  }

  public String getConfigurations() {
    return configurations;
  }

  public void setConfigurations(String configurations) {
    this.configurations = configurations;
  }

  public String getExtractors() {
    return extractors;
  }

  public void setExtractors(String extractors) {
    this.extractors = extractors;
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  public void setOutputFormat(String outputFormat) {
    this.outputFormat = outputFormat;
  }

}
