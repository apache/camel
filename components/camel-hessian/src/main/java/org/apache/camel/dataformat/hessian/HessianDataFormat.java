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
package org.apache.camel.dataformat.hessian;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianFactory;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * The <a href="http://camel.apache.org/data-format.html">data format</a> using
 * <a href="http://hessian.caucho.com/doc/hessian-serialization.html">Hessian
 * Serialization</a>.
 *
 * @since 2.17
 */
public class HessianDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final String FORMAT_NAME = "hessian";
    private boolean whitelistEnabled = true;
    private String allowedUnmarshallObjects;
    private String deniedUnmarshallObjects;

    @Override
    public String getDataFormatName() {
        return FORMAT_NAME;
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream outputStream) throws Exception {
        final Hessian2Output out = new Hessian2Output(outputStream);
        try {
            out.startMessage();
            out.writeObject(graph);
            out.completeMessage();
        } finally {
            out.flush();
            try {
                out.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        final Hessian2Input in;
        if (!whitelistEnabled) {
            in = new Hessian2Input(inputStream);
        } else {
            HessianFactory factory = new HessianFactory();
            if (ObjectHelper.isNotEmpty(allowedUnmarshallObjects)) {
                factory.allow(allowedUnmarshallObjects);
            }
            if (ObjectHelper.isNotEmpty(deniedUnmarshallObjects)) {
                factory.deny(deniedUnmarshallObjects);
            }
            in = factory.createHessian2Input(inputStream);
        }
        try {
            in.startMessage();
            final Object obj = in.readObject();
            in.completeMessage();
            return obj;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public void setWhitelistEnabled(boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
    }

    public String getAllowedUnmarshallObjects() {
        return allowedUnmarshallObjects;
    }

    public void setAllowedUnmarshallObjects(String allowedUnmarshallObjects) {
        this.allowedUnmarshallObjects = allowedUnmarshallObjects;
    }

    public String getDeniedUnmarshallObjects() {
        return deniedUnmarshallObjects;
    }

    public void setDeniedUnmarshallObjects(String deniedUnmarshallObjects) {
        this.deniedUnmarshallObjects = deniedUnmarshallObjects;
    }

}
