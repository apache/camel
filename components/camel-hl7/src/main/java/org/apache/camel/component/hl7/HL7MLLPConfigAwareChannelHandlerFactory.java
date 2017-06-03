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
package org.apache.camel.component.hl7;

import java.nio.charset.Charset;

import org.apache.camel.component.netty4.DefaultChannelHandlerFactory;

/**
 * Abstract helper for Netty decoder and encoder factory
 */
abstract class HL7MLLPConfigAwareChannelHandlerFactory extends DefaultChannelHandlerFactory {

    protected final HL7MLLPConfig config;

    HL7MLLPConfigAwareChannelHandlerFactory() {
        this(new HL7MLLPConfig());
    }

    HL7MLLPConfigAwareChannelHandlerFactory(HL7MLLPConfig config) {
        this.config = config;
    }
    
    public void setValidate(boolean validate) {
        config.setValidate(validate);
    }

    public void setCharset(Charset charset) {
        config.setCharset(charset);
    }

    public void setCharset(String charsetName) {
        config.setCharset(Charset.forName(charsetName));
    }

    public Charset getCharset() {
        return config.getCharset();
    }

    public boolean isConvertLFtoCR() {
        return config.isConvertLFtoCR();
    }

    public void setConvertLFtoCR(boolean convertLFtoCR) {
        config.setConvertLFtoCR(convertLFtoCR);
    }

    public char getStartByte() {
        return config.getStartByte();
    }

    public void setStartByte(char startByte) {
        config.setStartByte(startByte);
    }

    public char getEndByte1() {
        return config.getEndByte1();
    }

    public void setEndByte1(char endByte1) {
        config.setEndByte1(endByte1);
    }

    public char getEndByte2() {
        return config.getEndByte2();
    }

    public void setEndByte2(char endByte2) {
        config.setEndByte2(endByte2);
    }

    public boolean isProduceString() {
        return config.isProduceString();
    }

    public void setProduceString(boolean apply) {
        config.setProduceString(apply);
    }
}
