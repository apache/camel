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
package org.apache.camel.component.jdbc;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version $Revision:520964 $
 */
public class JdbcEndpoint extends DefaultEndpoint<DefaultExchange> {

    private URI uri;
    private String remaining;
    /** The maximum size for reading a result set <code>readSize</code> */
    private int readSize = 20000;

    protected JdbcEndpoint(String endpointUri, String remaining, JdbcComponent component) throws URISyntaxException {
        super(endpointUri, component);
        this.uri = new URI(endpointUri);
        this.remaining = remaining;
    }

    public JdbcEndpoint(String endpointUri, String remaining) throws URISyntaxException {
        super(endpointUri);
        this.remaining = remaining;
        this.uri = new URI(endpointUri);
    }

    public boolean isSingleton() {
        return false;
    }

    public Consumer<DefaultExchange> createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("A JDBC Consumer would be the server side of database! No such support here");
    }

    public Producer<DefaultExchange> createProducer() throws Exception {
        return new JdbcProducer(this, remaining, readSize);
    }

    public String getName() {
        String path = uri.getPath();
        if (path == null) {
            path = uri.getSchemeSpecificPart();
        }
        return path;
    }

    public int getReadSize() {
        return this.readSize;
    }

    public void setReadSize(int readSize) {
        this.readSize = readSize;
    }

}
