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
package org.apache.camel.component.beanstalk;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Beanstalk Camel component.
 * <p/>
 * URI is <code>beanstalk://[host[:port]][/tube]?query</code>
 * <p/>
 * Parameters:<ul>
 * <li><code>command</code> - one of "put", "release", "bury", "touch", "delete", "kick".
 * "put" is the default for Producers.</li>
 * <li><code>jobPriority</code></li>
 * <li><code>jobDelay</code></li>
 * <li><code>jobTimeToRun</code></li>
 * <li><code>consumer.onFailure</code></li>
 * <li><code>consumer.awaitJob</code></li>
 * </ul>
 *
 * @see BeanstalkEndpoint
 * @see ConnectionSettingsFactory
 */
public class BeanstalkComponent extends UriEndpointComponent {
    public static final String DEFAULT_TUBE = "default";

    public static final String COMMAND_BURY = "bury";
    public static final String COMMAND_RELEASE = "release";
    public static final String COMMAND_PUT = "put";
    public static final String COMMAND_TOUCH = "touch";
    public static final String COMMAND_DELETE = "delete";
    public static final String COMMAND_KICK = "kick";

    public static final long DEFAULT_PRIORITY = 1000; // 0 is highest
    public static final int DEFAULT_DELAY = 0;
    public static final int DEFAULT_TIME_TO_RUN = 60; // if 0 the daemon sets 1.

    private static ConnectionSettingsFactory connectionSettingsFactory = ConnectionSettingsFactory.DEFAULT;

    public BeanstalkComponent() {
        super(BeanstalkEndpoint.class);
    }

    @Override
    public boolean useRawUri() {
        return true;
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        return new BeanstalkEndpoint(uri, this, connectionSettingsFactory.parseUri(remaining), remaining);
    }

    /**
     * Custom {@link ConnectionSettingsFactory}.
     * <p/>
     * Specify which {@link ConnectionSettingsFactory} to use to make connections to Beanstalkd. Especially
     * useful for unit testing without beanstalkd daemon (you can mock {@link ConnectionSettings})
     *
     * @param connFactory the connection factory
     * @see ConnectionSettingsFactory
     */
    public static void setConnectionSettingsFactory(ConnectionSettingsFactory connFactory) {
        BeanstalkComponent.connectionSettingsFactory = connFactory;
    }

    public static ConnectionSettingsFactory getConnectionSettingsFactory() {
        return connectionSettingsFactory;
    }
}
