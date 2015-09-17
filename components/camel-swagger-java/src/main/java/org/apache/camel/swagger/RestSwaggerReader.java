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
package org.apache.camel.swagger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.swagger.config.SwaggerConfig;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;

public class RestSwaggerReader {

    public Swagger read(RestDefinition rest) {
        Swagger swagger = new Swagger();

        List<VerbDefinition> verbs = new ArrayList<>(rest.getVerbs());
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        Collections.sort(verbs, new VerbOrdering());

        // used during gathering of apis
        List<Path> paths = new ArrayList<>();

        String basePath = rest.getPath();

        for (VerbDefinition verb : verbs) {

            // TODO: should be base path and a lot more


            // the method must be in lower case
            String method = verb.asVerb().toLowerCase(Locale.US);

            String opPath = getPath(basePath, verb.getUri());

            Operation op = new Operation();

            Path path = swagger.getPath(opPath);
            if (path == null) {
                path = new Path();
                paths.add(path);
            }
            path = path.set(method, op);

            if (verb.getConsumes() != null) {
                op.consumes(verb.getConsumes());
            }
            if (verb.getProduces() != null) {
                op.produces(verb.getProduces());
            }
            if (verb.getDescriptionText() != null) {
                op.summary(verb.getDescriptionText());
            }

            // add path
            swagger.path(opPath, path);
        }

        return swagger;
    }

    private String getPath(String basePath, String uri) {
        // TODO: slash check and avoid double slash and all that
        return basePath + "/" + uri;
    }

    /**
     * To sort the rest operations
     */
    private static class VerbOrdering implements Comparator<VerbDefinition> {

        @Override
        public int compare(VerbDefinition a, VerbDefinition b) {

            String u1 = "";
            if (a.getUri() != null) {
                // replace { with _ which comes before a when soring by char
                u1 = a.getUri().replace("{", "_");
            }
            String u2 = "";
            if (b.getUri() != null) {
                // replace { with _ which comes before a when soring by char
                u2 = b.getUri().replace("{", "_");
            }

            int num = u1.compareTo(u2);
            if (num == 0) {
                // same uri, so use http method as sorting
                num = a.asVerb().compareTo(b.asVerb());
            }
            return num;
        }
    }


}
