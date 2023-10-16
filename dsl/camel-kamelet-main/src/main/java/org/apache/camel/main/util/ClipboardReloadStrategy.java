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
package org.apache.camel.main.util;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

@PeriodicTask("clipboard-reload-strategy")
public class ClipboardReloadStrategy extends ServiceSupport implements CamelContextAware, Runnable {

    private CamelContext camelContext;
    private final File file;
    private String content;

    public ClipboardReloadStrategy(File file) {
        this.file = file;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                content = IOHelper.loadText(fis);
            }
        }
    }

    @Override
    public void run() {
        // grab content from clipboard
        try {
            Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
            Object t = c.getData(DataFlavor.stringFlavor);
            if (t != null) {
                String text = t.toString();
                if (text != null && !text.isBlank() && (content == null || text.compareTo(content) != 0)) {
                    IOHelper.writeText(text, file);
                    content = text;
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
