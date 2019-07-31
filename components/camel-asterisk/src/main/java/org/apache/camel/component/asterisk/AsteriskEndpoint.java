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
package org.apache.camel.component.asterisk;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The asterisk component is used to interact with Asterisk PBX Server.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "asterisk", title = "Asterisk", syntax = "asterisk:name", label = "voip")
public class AsteriskEndpoint extends DefaultEndpoint {
    @UriPath(description = "Name of component")
    @Metadata(required = true)
    private String name;

    @UriParam
    @Metadata(required = true)
    private String hostname;

    @UriParam(label = "producer")
    private AsteriskAction action;

    @UriParam(secret = true)
    @Metadata(required = true)
    private String username;

    @UriParam(secret = true)
    @Metadata(required = true)
    private String password;

    public AsteriskEndpoint(String uri, AsteriskComponent component) {
        super(uri, component);
    }

    @Override
    protected void doStart() throws Exception {
        // Validate mandatory option
        ObjectHelper.notNull(hostname, "hostname");
        ObjectHelper.notNull(username, "username");
        ObjectHelper.notNull(password, "password");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AsteriskProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new AsteriskConsumer(this, processor);
    }

    public String getUsername() {
        return username;
    }

    /**
     * Login username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Login password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public AsteriskAction getAction() {
        return action;
    }

    /**
     * What action to perform such as getting queue status, sip peers or extension state.
     */
    public void setAction(AsteriskAction action) {
        this.action = action;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the asterisk server
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Logical name
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
