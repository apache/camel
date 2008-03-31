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
package org.apache.camel.component.uface.swing;

import java.awt.*;

import javax.swing.*;

import org.apache.camel.component.uface.UFaceBrowser;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.Main;
import org.ufacekit.ui.UIComposite;
import org.ufacekit.ui.swing.SwingComposite;
import org.ufacekit.ui.swing.SwingFactory;
import org.ufacekit.ui.swing.databinding.swing.SwingRealm;

/**
 * @version $Revision$
 */
public class SwingBrowser extends UFaceBrowser {
    static {
        SwingRealm.createDefault();
    }

    public SwingBrowser(DefaultCamelContext camelContext) {
        super(camelContext);
    }

    public void run() {
        JFrame frame = new JFrame();
        frame.setTitle("Camel Browser");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container container = frame.getContentPane();
        UIComposite composite = new SwingComposite(container, new SwingFactory().newFillLayout());

        createBrowserUI(composite);

        frame.pack();
        frame.setSize(650, 500);
        frame.setVisible(true);
    }
}
