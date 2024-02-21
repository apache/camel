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
package org.apache.camel.dataformat.swift.mx;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.prowidesoftware.JsonSerializable;
import com.prowidesoftware.swift.model.MxId;
import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxReadConfiguration;
import com.prowidesoftware.swift.model.mx.MxWriteConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * {@code SwiftMxDataFormat} is a specific {@link DataFormat} leveraging
 * <a href="https://github.com/prowide/prowide-iso20022">Prowide ISO 20022</a> to encode and decode SWIFT MX messages.
 */
@Dataformat("swiftMx")
@Metadata(firstVersion = "3.20.0", title = "SWIFT MX")
public class SwiftMxDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    /**
     * The specific configuration to use when marshalling a message. Ignored if {@code writeInJson} is set to
     * {@code true}.
     */
    private MxWriteConfiguration writeConfig;
    /**
     * The flag indicating that messages must be marshalled in a JSON format. If {@code true}, the value of
     * {@code writeConfig} is ignored.
     */
    private boolean writeInJson;
    /**
     * The type of MX message to produce when unmarshalling an input stream. If not set, it will be automatically
     * detected from the namespace used.
     */
    private MxId readMessageId;
    /**
     * The specific configuration to use when unmarshalling an input stream.
     */
    private MxReadConfiguration readConfig;

    /**
     * Constructs a {@code SwiftMxDataFormat} with the default configuration.
     */
    public SwiftMxDataFormat() {
    }

    /**
     * Constructs a {@code SwiftMxDataFormat} with the given parameters.
     *
     * @param writeInJson   the flag indicating that messages must be marshalled in a JSON format.
     * @param readMessageId the type of MX message to produce when unmarshalling an input stream. If not set, it will be
     *                      automatically detected from the namespace used.
     * @param readConfig    the specific configuration to use when unmarshalling an input stream.
     */
    public SwiftMxDataFormat(boolean writeInJson, MxId readMessageId, MxReadConfiguration readConfig) {
        this.writeInJson = writeInJson;
        this.readMessageId = readMessageId;
        this.readConfig = readConfig;
    }

    /**
     * Constructs a {@code SwiftMxDataFormat} with the given parameters.
     *
     * @param writeConfig   the specific configuration to use when marshalling a message.
     * @param readMessageId the type of MX message to produce when unmarshalling an input stream. If not set, it will be
     *                      automatically detected from the namespace used.
     * @param readConfig    the specific configuration to use when unmarshalling an input stream.
     */
    public SwiftMxDataFormat(MxWriteConfiguration writeConfig, MxId readMessageId, MxReadConfiguration readConfig) {
        this.writeConfig = writeConfig;
        this.readMessageId = readMessageId;
        this.readConfig = readConfig;
    }

    @Override
    public String getDataFormatName() {
        return "swiftMx";
    }

    @Override
    public void marshal(Exchange exchange, Object object, OutputStream stream) throws Exception {
        final String output;
        if (writeInJson) {
            output = ((JsonSerializable) object).toJson();
        } else {
            output = ((AbstractMX) object).message(writeConfig);
        }
        stream.write(output.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return AbstractMX.parse(
                new String(ExchangeHelper.convertToMandatoryType(exchange, byte[].class, stream), StandardCharsets.UTF_8),
                readMessageId, readConfig);
    }

    @Override
    public void init() {
        super.init();
        if (writeConfig == null) {
            writeConfig = new MxWriteConfiguration();
        }
        if (readConfig == null) {
            readConfig = new MxReadConfiguration();
        }
    }

    /**
     * @return the specific configuration to use when marshalling a message. Ignored if {@code writeInJson} is set to
     *         {@code true}.
     */
    public MxWriteConfiguration getWriteConfig() {
        return writeConfig;
    }

    public void setWriteConfig(Object writeConfig) {
        if (writeConfig != null) {
            if (writeConfig instanceof MxWriteConfiguration) {
                this.writeConfig = (MxWriteConfiguration) writeConfig;
            } else {
                throw new IllegalArgumentException(
                        String.format("The argument for setWriteConfig should be subClass of %s",
                                MxWriteConfiguration.class.getName()));
            }
        }
    }

    /**
     * @return the type of MX message to produce when unmarshalling an input stream. If not set, it will be
     *         automatically detected from the namespace used.
     */
    public MxId getReadMessageId() {
        return readMessageId;
    }

    public void setReadMessageId(MxId readMessageId) {
        this.readMessageId = readMessageId;
    }

    /**
     * @return the specific configuration to use when unmarshalling an input stream.
     */
    public MxReadConfiguration getReadConfig() {
        return readConfig;
    }

    public void setReadConfig(Object readConfig) {
        if (readConfig != null) {
            if (readConfig instanceof MxReadConfiguration) {
                this.readConfig = (MxReadConfiguration) readConfig;
            } else {
                throw new IllegalArgumentException(
                        String.format("The argument for setReadConfig should be subClass of %s",
                                MxReadConfiguration.class.getName()));
            }
        }
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
