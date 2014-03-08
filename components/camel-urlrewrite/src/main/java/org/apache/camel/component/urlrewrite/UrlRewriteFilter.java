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
package org.apache.camel.component.urlrewrite;

import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.IsSingleton;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tuckey.web.filters.urlrewrite.Conf;
import org.tuckey.web.filters.urlrewrite.RewrittenUrl;
import org.tuckey.web.filters.urlrewrite.UrlRewriter;
import org.tuckey.web.filters.urlrewrite.utils.ModRewriteConfLoader;

/**
 * Url rewrite filter based on <a href="https://code.google.com/p/urlrewritefilter/">url rewrite filter</a>
 * <p/>
 * See more details about the Camel <a href="http://camel.apache.org/urlrewrite">Url Rewrite</a> component.
 */
public abstract class UrlRewriteFilter extends ServiceSupport implements CamelContextAware, IsSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(UrlRewriteFilter.class);

    protected CamelContext camelContext;
    protected Conf conf;
    protected UrlRewriter urlRewriter;
    protected String configFile;
    protected String modRewriteConfFile;
    protected String modRewriteConfText;
    protected boolean useQueryString;
    protected boolean useContext;
    protected String defaultMatchType;
    protected String decodeUsing;

    public String rewrite(String url, HttpServletRequest request) throws Exception {
        RewrittenUrl response = urlRewriter.processRequest(request, null);
        if (response != null) {
            String answer = response.getTarget();
            LOG.debug("Rewrite url: {} -> {}", url, answer);
            return answer;
        } else {
            LOG.trace("Rewrite using original url: {}", url);
            return url;
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Conf getConf() {
        return conf;
    }

    public void setConf(Conf conf) {
        this.conf = conf;
    }

    public UrlRewriter getUrlRewriter() {
        return urlRewriter;
    }

    public void setUrlRewriter(UrlRewriter urlRewriter) {
        this.urlRewriter = urlRewriter;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getModRewriteConfText() {
        return modRewriteConfText;
    }

    public void setModRewriteConfText(String modRewriteConfText) {
        this.modRewriteConfText = modRewriteConfText;
    }

    public String getModRewriteConfFile() {
        return modRewriteConfFile;
    }

    public void setModRewriteConfFile(String modRewriteConfFile) {
        this.modRewriteConfFile = modRewriteConfFile;
    }

    public boolean isUseQueryString() {
        return useQueryString;
    }

    public void setUseQueryString(boolean useQueryString) {
        this.useQueryString = useQueryString;
    }

    public boolean isUseContext() {
        return useContext;
    }

    public void setUseContext(boolean useContext) {
        this.useContext = useContext;
    }

    public String getDefaultMatchType() {
        return defaultMatchType;
    }

    public void setDefaultMatchType(String defaultMatchType) {
        this.defaultMatchType = defaultMatchType;
    }

    public String getDecodeUsing() {
        return decodeUsing;
    }

    public void setDecodeUsing(String decodeUsing) {
        this.decodeUsing = decodeUsing;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext");

        if (conf == null) {
            if (modRewriteConfFile != null) {
                LOG.debug("Using mod rewrite config file: {} as config for urlRewrite", modRewriteConfFile);
                InputStream is = camelContext.getClassResolver().loadResourceAsStream(modRewriteConfFile);
                if (is == null) {
                    throw new IOException("Cannot load mod rewrite config file: " + modRewriteConfFile);
                }
                try {
                    String text = camelContext.getTypeConverter().mandatoryConvertTo(String.class, is);
                    ModRewriteConfLoader loader = new ModRewriteConfLoader();
                    conf = new Conf();
                    loader.process(text, conf);
                } finally {
                    IOHelper.close(is);
                }
            } else if (modRewriteConfText != null) {
                LOG.debug("Using modRewriteConfText: {} as config for urlRewrite", modRewriteConfText);
                ModRewriteConfLoader loader = new ModRewriteConfLoader();
                conf = new Conf();
                loader.process(modRewriteConfText, conf);
            } else if (configFile != null) {
                LOG.debug("Using config file: {} as config for urlRewrite", configFile);
                InputStream is = camelContext.getClassResolver().loadResourceAsStream(configFile);
                if (is == null) {
                    throw new IOException("Cannot load config file: " + configFile);
                }
                try {
                    conf = new Conf(is, configFile);
                } finally {
                    IOHelper.close(is);
                }
            }
            if (conf != null) {
                // set options before initializing
                conf.setUseQueryString(isUseQueryString());
                conf.setUseContext(isUseContext());
                if (getDefaultMatchType() != null) {
                    conf.setDefaultMatchType(getDefaultMatchType());
                }
                if (getDecodeUsing() != null) {
                    conf.setDecodeUsing(getDecodeUsing());
                }
                conf.initialise();
            }
            if (conf == null || !conf.isOk()) {
                throw new IllegalStateException("Error configuring config file: " + configFile);
            }
        }

        if (urlRewriter == null) {
            urlRewriter = new UrlRewriter(conf);
        }
    }

    @Override
    protected void doStop() throws Exception {
    }

    @Override
    protected void doShutdown() throws Exception {
        LOG.debug("Shutting down urlRewrite");
        urlRewriter.destroy();
        conf = null;
        urlRewriter = null;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
