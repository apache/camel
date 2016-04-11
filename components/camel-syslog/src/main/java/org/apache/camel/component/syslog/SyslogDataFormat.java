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
package org.apache.camel.component.syslog;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;

public class SyslogDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    @Override
    public String getDataFormatName() {
        return "syslog";
    }

    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        SyslogMessage message = ExchangeHelper.convertToMandatoryType(exchange, SyslogMessage.class, body);
        stream.write(SyslogConverter.toString(message).getBytes());
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        String body = ExchangeHelper.convertToMandatoryType(exchange, String.class, inputStream);
        SyslogMessage message = SyslogConverter.parseMessage(body.getBytes());

        exchange.getOut().setHeader(SyslogConstants.SYSLOG_FACILITY, message.getFacility());
        exchange.getOut().setHeader(SyslogConstants.SYSLOG_SEVERITY, message.getSeverity());
        exchange.getOut().setHeader(SyslogConstants.SYSLOG_HOSTNAME, message.getHostname());
        // use java.util.Date as timestamp
        Date time = message.getTimestamp() != null ? message.getTimestamp().getTime() : null;
        if (time != null) {
            exchange.getOut().setHeader(SyslogConstants.SYSLOG_TIMESTAMP, time);
        }

        // Since we are behind the fact of being in an Endpoint...
        // We need to pull in the remote/local via either Mina or Netty.

        if (exchange.getIn().getHeader("CamelMinaLocalAddress") != null) {
            message.setLocalAddress(exchange.getIn().getHeader("CamelMinaLocalAddress", String.class));
            exchange.getOut().setHeader(SyslogConstants.SYSLOG_LOCAL_ADDRESS, message.getLocalAddress());
        }

        if (exchange.getIn().getHeader("CamelMinaRemoteAddress") != null) {
            message.setRemoteAddress(exchange.getIn().getHeader("CamelMinaRemoteAddress", String.class));
            exchange.getOut().setHeader(SyslogConstants.SYSLOG_REMOTE_ADDRESS, message.getRemoteAddress());
        }

        if (exchange.getIn().getHeader("CamelNettyLocalAddress") != null) {
            message.setLocalAddress(exchange.getIn().getHeader("CamelNettyLocalAddress", String.class));
            exchange.getOut().setHeader(SyslogConstants.SYSLOG_LOCAL_ADDRESS, message.getLocalAddress());
        }

        if (exchange.getIn().getHeader("CamelNettyRemoteAddress") != null) {
            message.setRemoteAddress(exchange.getIn().getHeader("CamelNettyRemoteAddress", String.class));
            exchange.getOut().setHeader(SyslogConstants.SYSLOG_REMOTE_ADDRESS, message.getRemoteAddress());
        }

        return message;
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
