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
package org.apache.camel.component.shell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("shell")
public class ShellComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(ShellComponent.class);

    @Metadata(label = "advanced", defaultValue = "true", description = "Whether to print the Camel banner on startup")
    private boolean showBanner = true;

    @Metadata(label = "advanced", defaultValue = "camel-shell-banner.txt",
              description = "Classpath resource path to a custom banner text file")
    private String bannerResource = "camel-shell-banner.txt";

    private Terminal terminal;
    private LineReader lineReader;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        try {
            terminal = TerminalBuilder.builder().system(true).build();
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
            if (showBanner) {
                String banner = loadBanner();
                if (banner != null) {
                    String colored = new AttributedStringBuilder()
                            .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                            .append(banner)
                            .style(AttributedStyle.DEFAULT)
                            .toAnsi(terminal);
                    terminal.writer().println(colored);
                    terminal.writer().flush();
                }
            }
            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .highlighter(new Highlighter() {
                        @Override
                        public AttributedString highlight(LineReader reader, String buffer) {
                            return new AttributedStringBuilder()
                                    .style(AttributedStyle.BOLD)
                                    .append(buffer)
                                    .toAttributedString();
                        }

                        @Override
                        public void setErrorPattern(Pattern errorPattern) {
                        }

                        @Override
                        public void setErrorIndex(int errorIndex) {
                        }
                    })
                    .build();
        } catch (IOException e) {
            LOG.error("Failed to create JLine terminal.", e);
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                LOG.warn("Error closing JLine terminal", e);
            }
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ShellEndpoint endpoint = new ShellEndpoint(uri, this);
        endpoint.setPrompt(remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private String loadBanner() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(bannerResource)) {
            if (is == null) {
                LOG.warn("Banner resource not found: {}", bannerResource);
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to load banner resource: {}", bannerResource, e);
            return null;
        }
    }

    public boolean isShowBanner() {
        return showBanner;
    }

    public void setShowBanner(boolean showBanner) {
        this.showBanner = showBanner;
    }

    public String getBannerResource() {
        return bannerResource;
    }

    public void setBannerResource(String bannerResource) {
        this.bannerResource = bannerResource;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public LineReader getLineReader() {
        return lineReader;
    }
}
