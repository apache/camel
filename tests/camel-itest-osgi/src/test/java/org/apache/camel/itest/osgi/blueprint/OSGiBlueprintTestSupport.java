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
package org.apache.camel.itest.osgi.blueprint;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.ops4j.store.Store;
import org.ops4j.store.StoreFactory;

/**
 * Base class for OSGi blueprint tests.
 */
public abstract class OSGiBlueprintTestSupport extends AbstractIntegrationTest {

    protected static void copy(InputStream input, OutputStream output, boolean close) throws IOException {
        try {
            byte[] buf = new byte[8192];
            int bytesRead = input.read(buf);
            while (bytesRead != -1) {
                output.write(buf, 0, bytesRead);
                bytesRead = input.read(buf);
            }
            output.flush();
        } finally {
            if (close) {
                close(input, output);
            }
        }
    }

    protected static void close(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable c : closeables) {
                try {
                    c.close();
                } catch (IOException e) {
                }
            }
        }
    }

    protected static UrlProvisionOption bundle(final InputStream stream) throws IOException {
        Store<InputStream> store = StoreFactory.defaultStore();
        return new UrlProvisionOption(store.getLocation(store.store(stream)).toURL().toExternalForm());
    }

}
