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
package org.apache.camel.component.mtconnect;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * Receive the observations of an MTConnect agent through its streaming sample request or by polling it, one message per
 * observation, or send the probe, current, sample and asset requests on demand.
 */
@UriEndpoint(firstVersion = "4.24.0", scheme = "mtconnect", title = "MTConnect", syntax = "mtconnect:agentUrl",
             category = { Category.IOT }, headersClass = MTConnectConstants.class)
public class MTConnectEndpoint extends ScheduledPollEndpoint {

    @UriPath(label = "common", description = "The agent address: http or https, host, port and an optional base path,"
                                             + " such as http://agent:5000.")
    @Metadata(required = true)
    private final String agentUrl;
    @UriParam(label = "common", description = "The device name or uuid. Without it the consumer receives every device"
                                              + " the agent serves.")
    private String device;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the consumer reads the agent's streaming sample request. When false, it polls the"
                            + " agent on Camel's scheduler with one probe, current or sample request per poll, for"
                            + " agents behind a proxy or firewall that cuts long-lived responses; the scheduler options"
                            + " such as delay, initialDelay and greedy apply only then, and the delay is also the wait"
                            + " after a failure.")
    private boolean streaming = true;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer also sends asset messages: every asset the agent holds, removed ones"
                            + " included, after the current values and after each resynchronisation or agent restart,"
                            + " and the asset of each AssetChanged or AssetRemoved observation. The body is the asset"
                            + " element as a standalone XML document.")
    private boolean assets;
    @UriParam(label = "consumer", defaultValue = "500",
              description = "Streaming only: the minimum time in milliseconds between two parts of the agent's"
                            + " sample stream.")
    private long interval = 500;
    @UriParam(label = "consumer", defaultValue = "10000",
              description = "Streaming only: the time in milliseconds after which the agent sends an empty part when"
                            + " nothing changed. A stream without a part for two heartbeats counts as a failure.")
    private long heartbeat = 10000;
    @UriParam(label = "common", defaultValue = "1000",
              description = "The maximum number of observations in one part of the consumer's stream, in each"
                            + " sample request of the polling consumer and in the producer's sample reply, lowered to the"
                            + " agent's buffer size from the probe; also the maximum number of assets in the producer's"
                            + " assets reply.")
    private int count = 1000;
    @UriParam(label = "common", defaultValue = "5000",
              description = "The time in milliseconds to establish the connection to the agent.")
    private long connectTimeout = 5000;
    @UriParam(label = "common", defaultValue = "5000",
              description = "The time in milliseconds within which the agent's response headers must arrive after a"
                            + " request, and the longest silence while the rest of a document arrives. The parts of the"
                            + " streaming consumer's sample stream are bounded by two heartbeats instead.")
    private long readTimeout = 5000;
    @UriParam(label = "consumer", defaultValue = "10000",
              description = "Streaming only: the time in milliseconds to wait after a failed request, a failed or ended"
                            + " stream or an agent error before the next attempt.")
    private long reconnectDelay = 10000;
    @UriParam(label = "common,advanced",
              description = "An MTConnect XPath filter the agent applies to the current and sample requests, passed"
                            + " unchanged, such as //Axes//Linear to receive the linear axes only.")
    private String path;
    @UriParam(label = "common,security",
              description = "The TLS configuration for an https agent, such as a trust store with a private certificate"
                            + " authority. Without it, and without the component's global SSL context parameters, the"
                            + " runtime's default trust applies.")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer",
              description = "The request the producer sends: probe returns the agent's device model document, current"
                            + " the latest observations, sample a range of observations, assets the agent's asset"
                            + " document. The CamelMTConnectOperation header overrides it; with neither, the exchange"
                            + " fails.")
    private MTConnectOperation operation;
    @UriParam(label = "producer",
              description = "The sequence at which the current request reads the values, one the agent still buffers."
                            + " Without it the agent answers with its latest values.")
    private Long at;
    @UriParam(label = "producer",
              description = "The first sequence of the sample request. Without it the agent starts at the oldest"
                            + " sequence it buffers.")
    private Long from;
    @UriParam(label = "producer", description = "The last sequence the sample request includes.")
    private Long to;
    @UriParam(label = "common",
              description = "The asset type to read, such as CuttingTool or File: the standard's type parameter of the"
                            + " assets request, for the producer's assets operation and the consumer's asset messages."
                            + " Without it assets of every type are read.")
    private String assetType;
    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether the assets request also reads assets the agent marked removed.")
    private boolean removed;

    public MTConnectEndpoint(String uri, MTConnectComponent component, String agentUrl) {
        super(uri, component);
        this.agentUrl = agentUrl;
    }

    @Override
    public Producer createProducer() {
        return new MTConnectProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = streaming
                ? new MTConnectConsumer(this, processor) : new MTConnectPollingConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        validate();
    }

    private void validate() {
        URI uri;
        try {
            uri = new URI(agentUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The agent address is not a valid URI: " + agentUrl, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("The agent address must be an http or https URL with a host: " + agentUrl);
        }
        requireAtLeast("count", count, 1);
        requireAtLeast("heartbeat", heartbeat, 1);
        requireAtLeast("interval", interval, 0);
        requireAtLeast("connectTimeout", connectTimeout, 1);
        requireAtLeast("readTimeout", readTimeout, 1);
        requireAtLeast("reconnectDelay", reconnectDelay, 0);
    }

    /** The client for the agent with the endpoint's address, device, filter, timeouts and TLS configuration. */
    AgentHttpClient createClient(Executor executor) throws GeneralSecurityException, IOException {
        SSLContext sslContext = sslContextParameters == null ? null : sslContextParameters.createSSLContext(getCamelContext());
        return new AgentHttpClient(
                agentUrl, device, path, Duration.ofMillis(connectTimeout),
                Duration.ofMillis(readTimeout), sslContext, executor);
    }

    private static void requireAtLeast(String option, long value, long minimum) {
        if (value < minimum) {
            throw new IllegalArgumentException(option + " must be at least " + minimum + ", was " + value);
        }
    }

    public String getAgentUrl() {
        return agentUrl;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isAssets() {
        return assets;
    }

    public void setAssets(boolean assets) {
        this.assets = assets;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public long getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(long heartbeat) {
        this.heartbeat = heartbeat;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public MTConnectOperation getOperation() {
        return operation;
    }

    public void setOperation(MTConnectOperation operation) {
        this.operation = operation;
    }

    public Long getAt() {
        return at;
    }

    public void setAt(Long at) {
        this.at = at;
    }

    public Long getFrom() {
        return from;
    }

    public void setFrom(Long from) {
        this.from = from;
    }

    public Long getTo() {
        return to;
    }

    public void setTo(Long to) {
        this.to = to;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }
}
