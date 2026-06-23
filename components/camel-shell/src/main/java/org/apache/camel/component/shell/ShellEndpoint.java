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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

@UriEndpoint(firstVersion = "4.21.0", category = { Category.API }, scheme = "shell", title = "Shell",
             syntax = "shell:prompt", consumerOnly = true, remote = false)
public class ShellEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true, description = "Shell prompt")
    private String prompt;

    @UriParam(label = "consumer", defaultValue = "cyan",
              description = "Prompt color: black, red, green, yellow, blue, magenta, cyan, white")
    private String color = "cyan";

    public ShellEndpoint(String uri, ShellComponent component) {
        super(uri, component);
    }

    @Override
    public ShellComponent getComponent() {
        return (ShellComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("The shell component does not support a producer.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ShellConsumer consumer = new ShellConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Terminal getTerminal() {
        return getComponent().getTerminal();
    }

    public LineReader getLineReader() {
        return getComponent().getLineReader();
    }
}
