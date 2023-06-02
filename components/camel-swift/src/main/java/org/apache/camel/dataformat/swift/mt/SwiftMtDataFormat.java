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
package org.apache.camel.dataformat.swift.mt;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.prowidesoftware.JsonSerializable;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

/**
 * {@code SwiftMtDataFormat} is a specific {@link DataFormat} leveraging
 * <a href="https://github.com/prowide/prowide-core">Prowide Core</a> to encode and decode SWIFT MT messages.
 */
@Dataformat("swiftMt")
@Metadata(firstVersion = "3.20.0", title = "SWIFT MT")
public class SwiftMtDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    /**
     * The flag indicating that messages must be marshalled in a JSON format.
     */
    private boolean writeInJson;

    /**
     * Constructs a {@code SwiftMtDataFormat} with the default configuration.
     */
    public SwiftMtDataFormat() {
    }

    /**
     * Constructs a {@code SwiftMtDataFormat} with the given parameter.
     *
     * @param writeInJson the flag indicating that messages must be marshalled in a JSON format.
     */
    public SwiftMtDataFormat(boolean writeInJson) {
        this.writeInJson = writeInJson;
    }

    @Override
    public String getDataFormatName() {
        return "swiftMt";
    }

    @Override
    public void marshal(Exchange exchange, Object object, OutputStream stream) throws Exception {
        if (writeInJson) {
            stream.write(((JsonSerializable) object).toJson().getBytes(StandardCharsets.UTF_8));
        } else {
            ((AbstractMT) object).write(stream);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return AbstractMT.parse(stream);
    }

    /**
     * @return {@code true} if messages must be marshalled in a JSON format, {@code false} otherwise.
     */
    public boolean isWriteInJson() {
        return writeInJson;
    }

    public void setWriteInJson(boolean writeInJson) {
        this.writeInJson = writeInJson;
    }
}
