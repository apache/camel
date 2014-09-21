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
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Beanstalk Camel component.
 *
 * URI is <code>beanstalk://[host[:port]][/tube]?query</code>
 * <p>
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
 * @author <a href="mailto:azarov@osinka.com">Alexander Azarov</a>
 * @see BeanstalkEndpoint
 * @see ConnectionSettingsFactory
 */
public class BeanstalkComponent extends DefaultComponent {
    public static final String DEFAULT_TUBE     = "default";

    public final static String COMMAND_BURY     = "bury";
    public final static String COMMAND_RELEASE  = "release";
    public final static String COMMAND_PUT      = "put";
    public final static String COMMAND_TOUCH    = "touch";
    public final static String COMMAND_DELETE   = "delete";
    public final static String COMMAND_KICK     = "kick";

    public final static long DEFAULT_PRIORITY       = 1000; // 0 is highest
    public final static int  DEFAULT_DELAY          = 0;
    public final static int  DEFAULT_TIME_TO_RUN    = 60; // if 0 the daemon sets 1.

    static ConnectionSettingsFactory connFactory = ConnectionSettingsFactory.DEFAULT;

    public BeanstalkComponent() {
    }

    public BeanstalkComponent(final CamelContext context) {
        super(context);
    }

    @Override
    public boolean useRawUri() {
        return true;
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String,Object> parameters) throws Exception {
        return new BeanstalkEndpoint(uri, this, connFactory.parseUri(remaining));
    }

    /**
     * Custom ConnectionSettingsFactory.
     * <p>
     * Specify which {@link ConnectionSettingsFactory} to use to make connections to Beanstalkd. Especially
     * useful for unit testing without beanstalkd daemon (you can mock {@link ConnectionSettings})
     * 
     * @param connFactory
     * @see ConnectionSettingsFactory
     */
    public static void setConnectionSettingsFactory(ConnectionSettingsFactory connFactory) {
        BeanstalkComponent.connFactory = connFactory;
    }
}
