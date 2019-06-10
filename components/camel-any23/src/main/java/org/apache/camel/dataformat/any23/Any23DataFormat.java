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

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
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

  /**
   * Marshal data. Generate RDF.
   */
  public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
    throw new CamelException("Under construction");
  }

  /**
   * Unmarshal the data
   */
  public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {

     throw new CamelException("Under construction");
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
