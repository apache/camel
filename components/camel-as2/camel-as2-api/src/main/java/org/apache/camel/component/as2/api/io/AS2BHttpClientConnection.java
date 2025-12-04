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

package org.apache.camel.component.as2.api.io;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.ssl.SSLSession;

import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

public class AS2BHttpClientConnection implements ManagedHttpClientConnection {

    private ManagedHttpClientConnection mc;

    public AS2BHttpClientConnection(ManagedHttpClientConnection mc) {
        this.mc = mc;
    }

    @Override
    public void bind(Socket socket) throws IOException {
        mc.bind(socket);
    }

    @Override
    public Socket getSocket() {
        return mc.getSocket();
    }

    @Override
    public void close() throws IOException {
        mc.close();
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return mc.getEndpointDetails();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return mc.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return mc.getRemoteAddress();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return mc.getProtocolVersion();
    }

    @Override
    public SSLSession getSSLSession() {
        return mc.getSSLSession();
    }

    @Override
    public boolean isOpen() {
        return mc.isOpen();
    }

    @Override
    public void passivate() {
        mc.passivate();
    }

    @Override
    public void activate() {
        mc.activate();
    }

    @Override
    public boolean isConsistent() {
        return mc.isConsistent();
    }

    @Override
    public void sendRequestHeader(ClassicHttpRequest request) throws HttpException, IOException {
        mc.sendRequestHeader(request);
    }

    @Override
    public void terminateRequest(ClassicHttpRequest request) throws HttpException, IOException {
        mc.terminateRequest(request);
    }

    @Override
    public void sendRequestEntity(ClassicHttpRequest request) throws HttpException, IOException {
        mc.sendRequestEntity(request);
    }

    @Override
    public ClassicHttpResponse receiveResponseHeader() throws HttpException, IOException {
        return mc.receiveResponseHeader();
    }

    @Override
    public void receiveResponseEntity(ClassicHttpResponse response) throws HttpException, IOException {
        mc.receiveResponseEntity(response);
        EntityParser.parseAS2MessageEntity(response);
    }

    @Override
    public boolean isDataAvailable(Timeout timeout) throws IOException {
        return mc.isDataAvailable(timeout);
    }

    @Override
    public boolean isStale() throws IOException {
        return mc.isStale();
    }

    @Override
    public void flush() throws IOException {
        mc.flush();
    }

    @Override
    public Timeout getSocketTimeout() {
        return mc.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(Timeout timeout) {
        mc.setSocketTimeout(timeout);
    }

    @Override
    public void close(CloseMode closeMode) {
        mc.close(closeMode);
    }
}
