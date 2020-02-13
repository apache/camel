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
package org.apache.camel.dataformat.any23.utils;

import java.io.OutputStream;

import org.apache.any23.writer.JSONLDWriter;
import org.apache.any23.writer.NQuadsWriter;
import org.apache.any23.writer.NTriplesWriter;
import org.apache.any23.writer.RDFXMLWriter;
import org.apache.any23.writer.TripleHandler;
import org.apache.any23.writer.TurtleWriter;
import org.apache.camel.dataformat.any23.Any23OutputFormat;
import org.apache.camel.dataformat.any23.writer.RDF4JModelWriter;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;

public final class Any23Utils {

    private Any23Utils() {
    }

    public static TripleHandler obtainHandler(Any23OutputFormat format, OutputStream outputStream) {
        TripleHandler handler;
        switch (format) {
            case NTRIPLES:
                handler = new NTriplesWriter(outputStream);
                break;
            case TURTLE:
                handler = new TurtleWriter(outputStream);
                break;
            case NQUADS:
                handler = new NQuadsWriter(outputStream);
                break;
            case RDFXML:
                handler = new RDFXMLWriter(outputStream);
                break;
            case JSONLD:
                handler = new JSONLDWriter(outputStream);
                break;
            case RDFJSON:
                handler = new JSONLDWriter(outputStream);
                break;
            case RDF4JMODEL:
                handler = new RDF4JModelWriter(new LinkedHashModel());
                break;
            default:
                throw new AssertionError(format.name());
        }
        return handler;
    }

}
