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
import java.io.OutputStream;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Create, edit and read ISO-8583 messages.
 */
@Dataformat("iso8583")
@Metadata(firstVersion = "4.14.0", title = "ISO-8583")
public class Iso8583DataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

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
        int len = -1;
        // hex-based to int
        String iType = exchange.getMessage().getHeader(Iso8583Constants.ISO_TYPE, isoType, String.class);
        int i = Integer.parseUnsignedInt(iType, 16);
        String type = messageFactory.getIsoHeader(i);
        if (type != null) {
            len = type.length();
        }
        if (len == -1) {
            throw new IllegalArgumentException(
                    "IsoType " + iType + " is not known in the MessageFactory configuration file.");
        }
        return messageFactory.parseMessage(data, len);
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
            if (messageFactory == null) {
                messageFactory = new MessageFactory();
                if (configFile == null) {
                    configFile = "j8583-config.xml";
                }
            }
            if (configFile != null) {
                messageFactory.setConfigPath(configFile);
            }
        }
    }
}
