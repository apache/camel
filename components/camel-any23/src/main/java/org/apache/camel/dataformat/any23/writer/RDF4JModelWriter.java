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
package org.apache.camel.dataformat.any23.writer;

import org.apache.any23.writer.FormatWriter;
import org.apache.any23.writer.TripleHandlerException;
import org.apache.any23.writer.TripleWriterHandler;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;

public class RDF4JModelWriter extends TripleWriterHandler implements FormatWriter {

    private Model model;

    public RDF4JModelWriter(Model model) {
        this.model = model;
    }

    @Override
    public void close() throws TripleHandlerException {
        // noop
    }

    @Override
    public void writeTriple(Resource s, IRI p, Value o, Resource g) throws TripleHandlerException {
        model.add(s, p, o, g);
    }

    @Override
    public void writeNamespace(String prefix, String uri) throws TripleHandlerException {
        // noop
    }

    @Override
    public boolean isAnnotated() {
        return false;
    }

    @Override
    public void setAnnotated(boolean f) {
        // noop
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

}
