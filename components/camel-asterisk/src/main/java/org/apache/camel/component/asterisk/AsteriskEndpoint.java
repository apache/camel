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

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Receive events from an Asterisk PBX server and send actions to it over the Asterisk Manager Interface (AMI).
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "asterisk", title = "Asterisk", syntax = "asterisk:name",
             category = { Category.MOBILE }, headersClass = AsteriskConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "org.asteriskjava.manager.event.ManagerEvent",
                                   description = "The AMI event: a subclass of ManagerEvent such as NewChannelEvent or"
                                                 + " HangupEvent, with its simple class name in the CamelAsteriskEventName"
                                                 + " header. The ConnectEvent of the login comes first"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not read: the action comes from the action option or the"
                                                + " CamelAsteriskAction header, and the extension and context of"
                                                + " EXTENSION_STATE from the CamelAsteriskExtension and"
                                                + " CamelAsteriskContext headers"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "action", value = "QUEUE_STATUS", carriers = Carrier.POJO,
                                                          javaType = "org.asteriskjava.manager.ResponseEvents"),
                                           @BodyType.When(option = "action", value = "SIP_PEERS", carriers = Carrier.POJO,
                                                          javaType = "org.asteriskjava.manager.ResponseEvents"),
                                           @BodyType.When(option = "action", value = "EXTENSION_STATE",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.asteriskjava.manager.response.ManagerResponse") },
                                   description = "QUEUE_STATUS and SIP_PEERS give the ResponseEvents of asterisk-java: the"
                                                 + " ManagerResponse in getResponse() and the events of the list in"
                                                 + " getEvents(), such as QueueParamsEvent and QueueMemberEvent or"
                                                 + " PeerEntryEvent, ending with the complete event. EXTENSION_STATE gives"
                                                 + " the ManagerResponse, an ExtensionStateResponse. A ManagerError is the"
                                                 + " response when Asterisk answers with an error"))
public class AsteriskEndpoint extends DefaultEndpoint implements EndpointServiceLocation {
    @UriPath(description = "A logical name of the endpoint, for example the name of the Asterisk server. The component does"
                           + " not use it to connect.")
    @Metadata(required = true)
    private String name;

    @UriParam(description = "The host name or IP address of the Asterisk server. The consumer and the producer connect to"
                            + " the Asterisk Manager Interface (AMI) of this host on the port of the port option.")
    @Metadata(required = true)
    private String hostname;

    @UriParam(defaultValue = "5038",
              description = "The port of the Asterisk Manager Interface (AMI) of the Asterisk server, as set in the"
                            + " manager.conf file of the server.")
    private int port = AsteriskConnection.DEFAULT_PORT;

    @UriParam(label = "producer",
              description = "The AMI action that the producer sends for each message: QUEUE_STATUS for the status of the queues"
                            + " and their members, SIP_PEERS for the list of SIP peers, or EXTENSION_STATE for the state of the"
                            + " extension in the CamelAsteriskExtension and CamelAsteriskContext headers. The"
                            + " CamelAsteriskAction header replaces this option for a message. If neither is set, the"
                            + " producer fails the message.")
    private AsteriskAction action;

    @UriParam(label = "security", security = "secret",
              description = "The name of the AMI user, as defined in the manager.conf file of the Asterisk server. The consumer"
                            + " and the producer each log in with it when they start.")
    @Metadata(required = true)
    private String username;

    @UriParam(label = "security", security = "secret",
              description = "The secret of the AMI user, as defined in the manager.conf file of the Asterisk server.")
    @Metadata(required = true)
    private String password;

    public AsteriskEndpoint(String uri, AsteriskComponent component) {
        super(uri, component);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        // Validate mandatory option
        ObjectHelper.notNull(hostname, "hostname");
        ObjectHelper.notNull(username, "username");
        ObjectHelper.notNull(password, "password");
    }

    @Override
    public Producer createProducer() {
        return new AsteriskProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new AsteriskConsumer(this, processor);
    }

    @Override
    public String getServiceUrl() {
        return hostname;
    }

    @Override
    public String getServiceProtocol() {
        return "voip";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (username != null) {
            return Map.of("username", username);
        }
        return null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AsteriskAction getAction() {
        return action;
    }

    public void setAction(AsteriskAction action) {
        this.action = action;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
