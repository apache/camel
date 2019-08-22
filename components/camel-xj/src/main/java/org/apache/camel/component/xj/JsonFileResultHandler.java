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
package org.apache.camel.component.xj;

import java.io.File;

import javax.xml.transform.Result;
import javax.xml.transform.stax.StAXResult;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.camel.Message;
import org.apache.camel.component.xslt.ResultHandler;

/**
 * Result handler impl. to write a json document into a {@link File}
 */
public class JsonFileResultHandler implements ResultHandler {

    private final File file;
    private final Result result;

    /**
     * Creates a new json to file result handler instance
     *
     * @param jsonFactory the {@link JsonFactory} to use to write the json.
     */
    public JsonFileResultHandler(JsonFactory jsonFactory, File file) throws Exception {
        this.file = file;
        final JsonGenerator jsonGenerator = jsonFactory.createGenerator(this.file, JsonEncoding.UTF8);
        this.result = new StAXResult(new XmlJsonStreamWriter(jsonGenerator));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result getResult() {
        return this.result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBody(Message in) {
        in.setBody(this.file);
    }
}
