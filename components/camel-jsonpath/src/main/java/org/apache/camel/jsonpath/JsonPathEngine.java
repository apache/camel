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
package org.apache.camel.jsonpath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Configuration.Defaults;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.internal.DefaultsImpl;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConverter;

public class JsonPathEngine {

    private final JsonPath path;
    private final Configuration configuration;

    public JsonPathEngine(String expression) {
        Defaults defaults = DefaultsImpl.INSTANCE;
        this.configuration = Configuration.builder().jsonProvider(defaults.jsonProvider()).options(Option.SUPPRESS_EXCEPTIONS).build();
        this.path = JsonPath.compile(expression);
    }

    public Object read(Exchange exchange) throws IOException, InvalidPayloadException {
        Object json = exchange.getIn().getBody();

        if (json instanceof GenericFile) {
            try {
                json = GenericFileConverter.genericFileToInputStream((GenericFile<?>)json, exchange);
            } catch (NoTypeConversionAvailableException e) {
                json = ((WrappedFile<?>)json).getFile();
            }
        } else if (json instanceof WrappedFile) {
            json = ((WrappedFile<?>)json).getFile();
        }

        // the message body type should use the suitable read method
        if (json instanceof String) {
            String str = (String)json;
            return path.read(str, configuration);
        } else if (json instanceof InputStream) {
            InputStream is = (InputStream)json;
            return path.read(is, Charset.defaultCharset().displayName(), configuration);
        } else if (json instanceof File) {
            File file = (File)json;
            return path.read(file, configuration);
        } else if (json instanceof URL) {
            URL url = (URL)json;
            return path.read(url, configuration);
        }

        // fallback as input stream
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
        return path.read(is);
    }
}
