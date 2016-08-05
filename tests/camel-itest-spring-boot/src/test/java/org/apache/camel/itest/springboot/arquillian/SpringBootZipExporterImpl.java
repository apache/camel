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
package org.apache.camel.itest.springboot.arquillian;

import java.io.InputStream;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.impl.base.exporter.AbstractExporterDelegate;
import org.jboss.shrinkwrap.impl.base.exporter.AbstractStreamExporterImpl;

/**
 * An implementation of the zip exporter that does not compress entries,
 * for compatibility with spring-boot nested jar structure.
 */
public class SpringBootZipExporterImpl extends AbstractStreamExporterImpl implements ZipExporter {

    public SpringBootZipExporterImpl(Archive<?> archive) {
        super(archive);
    }

    @Override
    public InputStream exportAsInputStream() {
        // Create export delegate
        final AbstractExporterDelegate<InputStream> exportDelegate = new SpringBootZipExporterDelegate(this.getArchive());

        // Export and get result
        return exportDelegate.export();
    }

}
