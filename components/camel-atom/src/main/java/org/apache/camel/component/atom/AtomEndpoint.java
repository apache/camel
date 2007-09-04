/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.atom;

import org.apache.abdera.Abdera;
import org.apache.abdera.factory.Factory;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.util.iri.IRISyntaxException;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultPollingEndpoint;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * An <a href="http://activemq.apache.org/camel/atom.html">Atom Endpoint</a>.
 *
 * @version $Revision: 1.1 $
 */
public class AtomEndpoint extends DefaultPollingEndpoint {
    private Factory atomFactory;
    private String atomUri;

    public AtomEndpoint(String endpointUri, AtomComponent component, String atomUri) {
        super(endpointUri, component);
        this.atomUri = atomUri;
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        return new AtomProducer(this);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        return new AtomPollingConsumer(this);
    }

    public Document<Feed> parseDocument() throws IRISyntaxException, IOException {
        String uri = getAtomUri();
        InputStream in = new URL(uri).openStream();
        return createAtomParser().parse(in, uri);
    }

    public OutputStream createProducerOutputStream() throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(getAtomUri()));
    }

    // Properties
    //-------------------------------------------------------------------------
    public Factory getAtomFactory() {
        if (atomFactory == null) {
            atomFactory = createAtomFactory();
        }
        return atomFactory;
    }

    public void setAtomFactory(Factory atomFactory) {
        this.atomFactory = atomFactory;
    }

    public String getAtomUri() {
        return atomUri;
    }

    public void setAtomUri(String atomUri) {
        this.atomUri = atomUri;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected Factory createAtomFactory() {
        return Abdera.getNewFactory();
    }

    protected Parser createAtomParser() {
        return Abdera.getNewParser();
    }
}
