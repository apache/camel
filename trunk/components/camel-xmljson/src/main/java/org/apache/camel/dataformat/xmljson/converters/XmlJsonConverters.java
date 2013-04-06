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
package org.apache.camel.dataformat.xmljson.converters;

import java.io.IOException;
import java.io.InputStream;

import net.sf.json.JSON;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;

/**
 * Contains type converters to cater for Camel's unconditional conversion of the message body to an 
 * InputStream prior to marshaling
 */
@Converter
public final class XmlJsonConverters {

    private XmlJsonConverters() {
        // Helper class
    }
    
    /**
     * Converts from an existing JSON object circulating as such to an
     * InputStream, by dumping it to a String first and then using camel-core's
     * {@link IOConverter#toInputStream(String, Exchange)}
     */
    @Converter
    public static InputStream fromJSONtoInputStream(JSON json, Exchange exchange) throws IOException {
        return IOConverter.toInputStream(json.toString(), exchange);
    }

}
