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
package org.apache.camel.component.jte;

import java.nio.file.Path;
import java.util.Map;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("jte")
public class JteComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(JteComponent.class);

    private JteCodeResolver codeResolver;
    private TemplateEngine templateEngine;

    @Metadata(defaultValue = "jte-classes")
    private String workDir = "jte-classes";
    @Metadata
    private boolean preCompile;
    @Metadata(defaultValue = "Plain")
    private ContentType contentType = ContentType.Plain;
    @Metadata
    private boolean allowTemplateFromHeader;
    @Metadata
    private boolean allowContextMapAll;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        JteEndpoint endpoint = new JteEndpoint(uri, this, remaining);
        endpoint.setAllowTemplateFromHeader(allowTemplateFromHeader);
        endpoint.setAllowContextMapAll(allowContextMapAll);

        setProperties(endpoint, parameters);

        // if its a http resource then append any remaining parameters and update the resource uri
        if (ResourceHelper.isHttpUri(remaining)) {
            remaining = ResourceHelper.appendParameters(remaining, parameters);
            endpoint.setResourceUri(remaining);
        }

        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Path dir = Path.of(workDir);
        if (preCompile) {
            LOG.info("Using pre-compiling JTE templates: {}", workDir);
            templateEngine
                    = TemplateEngine.createPrecompiled(dir, contentType, getCamelContext().getApplicationContextClassLoader());
        } else {
            LOG.info("Using runtime compiled JTE templates: {}", workDir);
            codeResolver = new JteCodeResolver(getCamelContext());
            templateEngine = TemplateEngine.create(codeResolver, dir, contentType,
                    getCamelContext().getApplicationContextClassLoader());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (templateEngine != null) {
            templateEngine.cleanAll();
        }
    }

    public TemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    public JteCodeResolver getCodeResolver() {
        return codeResolver;
    }

    public String getWorkDir() {
        return workDir;
    }

    /**
     * Work directory where JTE will store compiled templates.
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public boolean isPreCompile() {
        return preCompile;
    }

    /**
     * To speed up startup and rendering on your production server, it is possible to precompile all templates during
     * the build. This way, the template engine can load each template's .class file directly without first compiling
     * it.
     */
    public void setPreCompile(boolean preCompile) {
        this.preCompile = preCompile;
    }

    public ContentType getContentType() {
        return contentType;
    }

    /**
     * Content type the JTE engine should use.
     */
    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public boolean isAllowContextMapAll() {
        return allowContextMapAll;
    }

    /**
     * Sets whether the context map should allow access to all details. By default only the message body and headers can
     * be accessed. This option can be enabled for full access to the current Exchange and CamelContext. Doing so impose
     * a potential security risk as this opens access to the full power of CamelContext API.
     */
    public void setAllowContextMapAll(boolean allowContextMapAll) {
        this.allowContextMapAll = allowContextMapAll;
    }

}
