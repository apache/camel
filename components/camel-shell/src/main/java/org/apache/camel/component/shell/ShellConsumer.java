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

import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ShellConsumer.class);

    private ExecutorService executorService;

    public ShellConsumer(ShellEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public ShellEndpoint getEndpoint() {
        return (ShellEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executorService
                = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "ShellConsumer");
        executorService.submit(this::run);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(executorService);
        }
    }

    private void run() {
        try {
            LineReader lineReader = getEndpoint().getLineReader();
            Terminal terminal = getEndpoint().getTerminal();
            int color = resolveColor(getEndpoint().getColor());

            while (isRunAllowed()) {
                String promptText = (getEndpoint().getPrompt() == null ? "" : getEndpoint().getPrompt()) + "> ";
                String coloredPrompt = new AttributedStringBuilder()
                        .style(AttributedStyle.DEFAULT.foreground(color).bold())
                        .append(promptText)
                        .style(AttributedStyle.DEFAULT)
                        .toAnsi(terminal);

                String line;
                try {
                    line = lineReader.readLine(coloredPrompt);
                } catch (Exception e) {
                    LOG.error("Error reading line from shell", e);
                    break;
                }

                if (line == null) {
                    LOG.info("Shell consumer received EOF (null line), exiting loop.");
                    break;
                }

                if ("exit".equalsIgnoreCase(line.trim())) {
                    LOG.info("Shell consumer received exit command, exiting loop.");
                    break;
                }

                Exchange exchange = getEndpoint().createExchange();
                exchange.getIn().setBody(line);
                try {
                    getProcessor().process(exchange);
                    Exception routeException = exchange.getException();
                    if (routeException != null) {
                        printError(terminal, "[ERROR] " + routeException.getMessage());
                    } else {
                        String output = exchange.getMessage().getBody(String.class);
                        if (output != null) {
                            terminal.writer().println(output);
                        }
                    }
                    terminal.writer().flush();
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                } finally {
                    releaseExchange(exchange, false);
                }
            }
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        } finally {
            LOG.info("Shell consumer loop finished.");
            getEndpoint().getCamelContext().stop();
        }
    }

    private static void printError(Terminal terminal, String message) {
        String errorLine = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold())
                .append(message)
                .style(AttributedStyle.DEFAULT)
                .toAnsi(terminal);
        terminal.writer().println(errorLine);
        terminal.writer().flush();
    }

    static int resolveColor(String name) {
        if (name == null) {
            return AttributedStyle.CYAN;
        }
        return switch (name.toLowerCase()) {
            case "black" -> AttributedStyle.BLACK;
            case "red" -> AttributedStyle.RED;
            case "green" -> AttributedStyle.GREEN;
            case "yellow" -> AttributedStyle.YELLOW;
            case "blue" -> AttributedStyle.BLUE;
            case "magenta" -> AttributedStyle.MAGENTA;
            case "white" -> AttributedStyle.WHITE;
            default -> AttributedStyle.CYAN;
        };
    }
}
