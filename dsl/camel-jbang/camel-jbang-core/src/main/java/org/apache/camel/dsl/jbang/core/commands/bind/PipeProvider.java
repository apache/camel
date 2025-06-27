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

package org.apache.camel.dsl.jbang.core.commands.bind;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asStringSet;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

/**
 * Binding to Kamelets as Kubernetes object references. Automatically resolves Kamelet from catalog and reads required
 * properties. Adds required properties as placeholder to the object reference when not set already by the user.
 */
public class PipeProvider extends ObjectReferenceBindingProvider {

    private static final String prefix = "kamelet:";

    public PipeProvider() {
        super("camel.apache.org/v1", "Kamelet");
    }

    @Override
    public String getEndpoint(
            EndpointType type, String uriExpression, Map<String, Object> endpointProperties, TemplateProvider templateProvider)
            throws Exception {
        if (uriExpression.startsWith(prefix)) {
            return super.getEndpoint(type, StringHelper.after(uriExpression, prefix), endpointProperties, templateProvider);
        }

        return super.getEndpoint(type, uriExpression, endpointProperties, templateProvider);
    }

    @Override
    protected Map<String, Object> getEndpointUriProperties(
            EndpointType type, String objectName, String uriExpression, Map<String, Object> endpointProperties)
            throws Exception {
        return kameletProperties(objectName,
                super.getEndpointUriProperties(type, objectName, uriExpression, endpointProperties));
    }

    /**
     * Get required properties from Kamelet specification and add those to the given user properties if not already set.
     * In case a required property is not present in the provided user properties the value is either set to the example
     * coming from the Kamelet specification or to a placeholder value for users to fill in manually. Property values do
     * already have quotes when the type is String.
     *
     * @param  kamelet
     * @return
     * @throws Exception
     */
    protected Map<String, Object> kameletProperties(String kamelet, Map<String, Object> userProperties) throws Exception {
        Map<String, Object> endpointProperties = new HashMap<>();
        InputStream is;
        String loc;
        Resource res;

        // try local disk first before GitHub
        ResourceResolver resolver = new DefaultResourceResolvers.FileResolver();
        try {
            res = resolver.resolve("file:" + kamelet + ".kamelet.yaml");
        } finally {
            resolver.close();
        }
        if (res.exists()) {
            is = res.getInputStream();
            loc = res.getLocation();
        } else {
            resolver = new GitHubResourceResolver();
            try {
                res = resolver.resolve(
                        "github:apache:camel-kamelets:main:kamelets/" + kamelet + ".kamelet.yaml");
            } finally {
                resolver.close();
            }
            loc = res.getLocation();
            URL u = new URL(loc);
            is = u.openStream();
        }
        if (is != null) {
            try {
                LoadSettings local = LoadSettings.builder().setLabel(loc).build();
                final StreamReader reader = new StreamReader(local, new YamlUnicodeReader(is));
                final Parser parser = new ParserImpl(local, reader);
                final Composer composer = new Composer(local, parser);
                Node root = composer.getSingleNode().orElse(null);
                if (root != null) {
                    Set<String> required = asStringSet(nodeAt(root, "/spec/definition/required"));
                    if (required != null && !required.isEmpty()) {
                        for (String req : required) {
                            if (!userProperties.containsKey(req)) {
                                String type = asText(nodeAt(root, "/spec/definition/properties/" + req + "/type"));
                                String example = asText(nodeAt(root, "/spec/definition/properties/" + req + "/example"));
                                StringBuilder vb = new StringBuilder();
                                if (example != null) {
                                    if ("string".equals(type)) {
                                        vb.append("\"");
                                    }
                                    vb.append(example);
                                    if ("string".equals(type)) {
                                        vb.append("\"");
                                    }
                                } else {
                                    vb.append("\"value\"");
                                }
                                endpointProperties.put(req, vb.toString());
                            }
                        }
                    }
                }
                IOHelper.close(is);
            } catch (Exception e) {
                System.err.println("Error parsing Kamelet: " + loc + " due to: " + e.getMessage());
            }
        } else {
            System.err.println("Kamelet not found on github: " + kamelet);
        }

        endpointProperties.putAll(userProperties);

        return endpointProperties;
    }

    @Override
    public boolean canHandle(String uriExpression) {
        return uriExpression.startsWith(prefix) || !uriExpression.contains(":");
    }
}
