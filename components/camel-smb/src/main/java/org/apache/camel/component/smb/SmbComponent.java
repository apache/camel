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

package org.apache.camel.component.smb;

import java.net.URI;
import java.util.Map;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileComponent;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("smb")
public class SmbComponent extends GenericFileComponent<FileIdBothDirectoryInformation> {

    private static final Logger LOG = LoggerFactory.getLogger(SmbComponent.class);

    public static final String SMB_FILE_INPUT_STREAM = "CamelSmbFileInputStream";

    public SmbComponent() {}

    public SmbComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<FileIdBothDirectoryInformation> buildFileEndpoint(
            String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String baseUri = getBaseUri(uri);

        // lets make sure we create a new configuration as each endpoint can
        // customize its own version
        // must pass on baseUri to the configuration (see above)
        SmbConfiguration config = new SmbConfiguration(new URI(baseUri));

        // backwards compatible when path was query parameter
        String path = getAndRemoveParameter(parameters, "path", String.class);
        if (path != null) {
            config.setPath(path);
            LOG.warn(
                    "The path option should be specified in the context-path. Instead of using ?path=/mypath then specify this in the context-path in uri: "
                            + uri);
        }

        if (config.getShareName() == null) {
            throw new IllegalArgumentException("ShareName must be configured");
        }

        SmbEndpoint endpoint = new SmbEndpoint(uri, this, config);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void afterPropertiesSet(GenericFileEndpoint<FileIdBothDirectoryInformation> endpoint) throws Exception {
        // noop
    }

    /**
     * Get the base uri part before the options as they can be non URI valid such as the expression using $ chars and
     * the URI constructor will regard $ as an illegal character, and we don't want to enforce end users to escape the $
     * for the expression (file language)
     */
    protected String getBaseUri(String uri) {
        return StringHelper.before(uri, "?", uri);
    }
}
