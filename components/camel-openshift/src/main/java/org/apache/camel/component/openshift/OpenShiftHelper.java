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
package org.apache.camel.component.openshift;

import java.io.IOException;
import java.util.Locale;

import com.openshift.client.IApplication;
import com.openshift.client.IDomain;
import com.openshift.client.IGear;
import com.openshift.client.IGearGroup;
import com.openshift.client.IOpenShiftConnection;
import com.openshift.client.IUser;
import com.openshift.client.OpenShiftConnectionFactory;
import com.openshift.client.configuration.OpenShiftConfiguration;

public final class OpenShiftHelper {

    private static final String DEFAULT_OPENSHIFT_SERVER = "openshift.redhat.com";

    private OpenShiftHelper() {
    }

    public static String getOpenShiftServer(OpenShiftEndpoint endpoint) throws IOException {
        String answer = endpoint.getServer();
        if (answer == null) {
            answer = new OpenShiftConfiguration().getLibraServer();
        }
        if (answer == null) {
            answer = DEFAULT_OPENSHIFT_SERVER;
        }
        return answer;
    }

    public static IDomain loginAndGetDomain(OpenShiftEndpoint endpoint, String openshiftServer) {
        // grab all applications
        IOpenShiftConnection connection =
                new OpenShiftConnectionFactory().getConnection(endpoint.getClientId(), endpoint.getUsername(), endpoint.getPassword(), openshiftServer);

        IUser user = connection.getUser();

        IDomain domain;
        if (endpoint.getDomain() != null) {
            domain = user.getDomain(endpoint.getDomain());
        } else {
            domain = user.getDefaultDomain();
        }

        return domain;
    }

    public static String getStateForApplication(IApplication application) {
        for (IGearGroup group : application.getGearGroups()) {
            for (IGear gear : group.getGears()) {
                String state = gear.getState().getState().toLowerCase(Locale.ENGLISH);
                return state;
            }
        }
        return "unknown";
    }
}
