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
package org.apache.camel.dataformat.iso8583;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Create, edit and read ISO-8583 messages.
 */
@Dataformat("iso8583")
@Metadata(firstVersion = "4.14.0", title = "ISO-8583")
@BodyTypes(marshalIn = @BodyType(value = Carrier.POJO, javaType = "com.solab.iso8583.IsoMessage",
                                 description = "A j8583 IsoMessage, such as unmarshal gives. A Map is not converted"),
           marshalOut = @BodyType(value = Carrier.STREAM,
                                  description = "The ISO-8583 message in the stream cache, or a byte array when stream caching"
                                                + " is off: ASCII or binary, as the j8583 configuration says"),
           unmarshalIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                   description = "An ISO-8583 message of the ISO-Type in the CamelIso8583IsoType header, or"
                                                 + " else the isoType option: unmarshal fails without either"),
           unmarshalOut = @BodyType(value = Carrier.POJO, javaType = "com.solab.iso8583.IsoMessage",
                                    description = "A j8583 IsoMessage: getAt(n) gives field n"))
public class Iso8583DataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private static final String DEFAULT_CONFIG_FILE = "j8583-config.xml";

    private CamelContext camelContext;
    private MessageFactory messageFactory;
    private String configFile;
    private boolean allowAutoWiredMessageFormat = true;
    private String isoType;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "iso8583";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        IsoMessage iso = exchange.getContext().getTypeConverter().mandatoryConvertTo(IsoMessage.class, exchange, graph);
        byte[] data = iso.writeData();
        if (data != null && data.length > 0) {
            stream.write(data);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        byte[] data = camelContext.getTypeConverter().mandatoryConvertTo(byte[].class, exchange, stream);
        // hex-based to int
        String iType = exchange.getMessage().getHeader(Iso8583Constants.ISO_TYPE, isoType, String.class);
        if (iType == null) {
            throw new IllegalArgumentException(
                    "The ISO-Type of the message is unknown: set the isoType option of the iso8583 data format, or the "
                                               + Iso8583Constants.ISO_TYPE + " header");
        }
        int i = Integer.parseUnsignedInt(iType, 16);
        // the ISO header of a message type is optional, and it can be binary
        byte[] binaryHeader = messageFactory.getBinaryIsoHeader(i);
        if (binaryHeader != null) {
            return messageFactory.parseMessage(data, binaryHeader.length, true);
        }
        String header = messageFactory.getIsoHeader(i);
        return messageFactory.parseMessage(data, header != null ? header.length() : 0);
    }

    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    public void setMessageFactory(MessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getIsoType() {
        return isoType;
    }

    public void setIsoType(String isoType) {
        this.isoType = isoType;
    }

    public boolean isAllowAutoWiredMessageFormat() {
        return allowAutoWiredMessageFormat;
    }

    public void setAllowAutoWiredMessageFormat(boolean allowAutoWiredMessageFormat) {
        this.allowAutoWiredMessageFormat = allowAutoWiredMessageFormat;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (messageFactory == null) {
            if (allowAutoWiredMessageFormat) {
                messageFactory = CamelContextHelper.findSingleByType(camelContext, MessageFactory.class);
            }
            boolean mandatory = configFile != null;
            if (messageFactory == null) {
                messageFactory = new MessageFactory();
                if (configFile == null) {
                    configFile = DEFAULT_CONFIG_FILE;
                }
            }
            if (configFile != null) {
                configureMessageFactory(mandatory);
            }
        }
    }

    private void configureMessageFactory(boolean mandatory) throws Exception {
        // load with the Camel resource loader (classpath: by default, file:, http: and so on), as j8583 itself only
        // loads from the classpath
        InputStream is = mandatory
                ? ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, configFile)
                : ResourceHelper.resolveResourceAsInputStream(camelContext, configFile);
        if (is != null) {
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                ConfigParser.configureFromReader(messageFactory, reader);
            }
        }
    }
}
