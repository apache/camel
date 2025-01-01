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
package org.apache.camel.component.smb2;

import java.util.Map;

import com.hierynomus.smbj.share.File;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Smb2Endpoint extends GenericFileEndpoint<File> implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(Smb2Endpoint.class);

    @UriParam
    protected Smb2Configuration configuration;

    protected Smb2Endpoint(String uri, Smb2Component component, Smb2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public boolean isSingletonProducer() {
        // this producer is stateful because the smb file operations is not
        // thread safe
        return false;
    }

    @Override
    public String getScheme() {
        return "smb2";
    }

    @Override
    public char getFileSeparator() {
        return '/';
    }

    @Override
    public boolean isAbsolute(String name) {
        return name.startsWith("/");
    }

    @Override
    public String getServiceUrl() {
        return configuration.getProtocol() + ":" + configuration.getHostname() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return configuration.getProtocol();
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getUsername() != null) {
            return Map.of("username", configuration.getUsername());
        }
        return null;
    }

    @Override
    public Exchange createExchange(GenericFile<File> file) {
        Exchange answer = super.createExchange();
        if (file != null) {
            file.bindToExchange(answer);
        }
        return answer;
    }

}
