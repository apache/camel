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
package org.apache.camel.impl.console;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "java-security", description = "Displays Java Security (JSSE) information")
@Configurer(extended = true)
public class JavaSecurityDevConsole extends AbstractDevConsole {

    public record ServiceEntry(
            @Metadata(description = "The service type") String type,
            @Metadata(description = "The algorithm") String algorithm,
            @Metadata(description = "The implementation class name") String className) {
    }

    public record ProviderEntry(
            @Metadata(description = "The provider name") String name,
            @Metadata(description = "The provider version") String version,
            @Metadata(description = "The provider info (only present when known)") String info,
            @Metadata(description = "The services offered by this provider (only present when any)") List<ServiceEntry> services) {
    }

    public record Response(
            @Metadata(description = "The security providers (only present when any are registered)") List<ProviderEntry> securityProviders) {
    }

    public JavaSecurityDevConsole() {
        super("jvm", "java-security", "Java Security", "Displays Java Security (JSSE) information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Provider[] providers = Security.getProviders();
        if (providers != null && providers.length > 0) {
            sb.append("Security Providers:");
            sb.append("\n");
            for (Provider p : providers) {
                sb.append(String.format("%n    %s (%s)%n", p.getName(), p.getVersionStr()));
                if (p.getInfo() != null) {
                    sb.append(String.format("%n        %s%n", p.getInfo()));
                }
                List<Provider.Service> services = p.getServices().stream()
                        .sorted(JavaSecurityDevConsole::compare)
                        .toList();
                for (Provider.Service s : services) {
                    sb.append(String.format("%n        %s: %s (%s)", s.getType(), s.getAlgorithm(), s.getClassName()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<ProviderEntry> providerEntries = null;

        Provider[] providers = Security.getProviders();
        if (providers != null && providers.length > 0) {
            providerEntries = new ArrayList<>();
            for (Provider p : providers) {
                List<Provider.Service> services = p.getServices().stream()
                        .sorted(JavaSecurityDevConsole::compare)
                        .toList();
                List<ServiceEntry> serviceEntries = null;
                if (!services.isEmpty()) {
                    serviceEntries = new ArrayList<>();
                    for (Provider.Service s : services) {
                        serviceEntries.add(new ServiceEntry(s.getType(), s.getAlgorithm(), s.getClassName()));
                    }
                }
                providerEntries.add(new ProviderEntry(p.getName(), p.getVersionStr(), p.getInfo(), serviceEntries));
            }
        }

        Response response = new Response(providerEntries);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static int compare(Provider.Service o1, Provider.Service o2) {
        int num = o1.getType().compareToIgnoreCase(o2.getType());
        if (num == 0) {
            num = o1.getAlgorithm().compareToIgnoreCase(o2.getAlgorithm());
        }
        return num;
    }

}
