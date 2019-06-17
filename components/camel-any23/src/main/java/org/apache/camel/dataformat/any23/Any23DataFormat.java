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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.any23.Any23;
import org.apache.any23.extractor.ExtractionException;
import org.apache.any23.http.HTTPClient;
import org.apache.any23.source.DocumentSource;
import org.apache.any23.source.HTTPDocumentSource;
import org.apache.any23.source.StringDocumentSource;
import org.apache.any23.writer.NTriplesWriter;
import org.apache.any23.writer.TripleHandler;
import org.apache.any23.writer.TripleHandlerException;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
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

  private Any23Parameters parameters ;

  /**
   * String or Node to return
   */
  private Class<?> dataObjectType;

  /**
   * What is the default output format ?
   */
  private String method;

  @Override
  public String getDataFormatName() {
    return "any23";
  }
  
  
  /*protected final void setDefaultParameters () {
     parameters = new Any23Parameters ();
  
  }*/
  
  /*public Any23DataFormat (){
    this.setDefaultParameters();
  }*/

  /**
   * Marshal data. Generate RDF.
   */
  public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
   /*final String payload = ExchangeHelper.convertToMandatoryType(exchange, String.class, object);
   System.out.print ("payload");
   System.out.print (payload);
   
    Any23 runner = new Any23();*/
    anytordf( exchange,  object,  outputStream);
           //  return n3;
   // throw new CamelException("Under construction");
  }

  /**
   * Unmarshal the data
   */
  public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
          Any23 runner = new Any23();
          runner.setHTTPUserAgent("test-user-agent");
          HTTPClient httpClient = runner.getHTTPClient();
          DocumentSource source = new HTTPDocumentSource(
                  httpClient,
                  "http://dbpedia.org/page/Ecuador");
        //  System.out.print("#######");
        //  System.out.print(source.getContentType());
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          TripleHandler handler = new NTriplesWriter(out);
           
              runner.extract(source, handler);
        
              
             handler.close();
            
          
             String n3 = out.toString("UTF-8");
            
           //  System.out.print (n3);
             return n3;
     //throw new CamelException("Under construction");
  }
  
  private void anytordf (final Exchange exchange, Object object, OutputStream outputStream) throws IOException, ExtractionException, TypeConversionException, NoTypeConversionAvailableException, TripleHandlerException{
    final String payload = ExchangeHelper.convertToMandatoryType(exchange, String.class, object);
    System.out.println ("PAYLOAD");
    System.out.println (payload);
    DocumentSource source = new StringDocumentSource(payload, "http://host.com/service");
    Any23 runner = new Any23();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    this.parameters = new Any23Parameters (out);
    runner.extract(source, this.parameters.getTripleHandlerOutput());
    this.parameters.getTripleHandlerOutput().close();
   // out.toString("UTF-8").get
   // out.toString("UTF-8");
   System.out.println("SALIDA");
    System.out.println(out.toString("UTF-8"));
    outputStream.write(out.toByteArray());
    
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
