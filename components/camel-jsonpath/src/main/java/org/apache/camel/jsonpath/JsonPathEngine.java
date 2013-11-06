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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.WrappedFile;

public class JsonPathEngine {

    private final JsonPath path;
    private final Configuration configuration;

    public JsonPathEngine(String expression) {
        this.configuration = Configuration.defaultConfiguration();
        this.path = JsonPath.compile(expression);
    }

    public Object read(Exchange exchange) throws IOException, InvalidPayloadException {
        Object json = exchange.getIn().getBody();

        if (json instanceof WrappedFile) {
            json = ((WrappedFile<?>) json).getFile();
        }

        // the message body type should use the suitable read method
        if (configuration.getProvider().isContainer(json)) {
            return path.read(json);
        } else if (json instanceof String) {
            String str = (String) json;
            return path.read(str);
        } else if (json instanceof InputStream) {
            InputStream is = (InputStream) json;
            return path.read(is);
        } else if (json instanceof File) {
            File file = (File) json;
            return path.read(file);
        } else if (json instanceof URL) {
            URL url = (URL) json;
            return path.read(url);
        }

        // fallback as input stream
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
        return path.read(is);
    }
}
