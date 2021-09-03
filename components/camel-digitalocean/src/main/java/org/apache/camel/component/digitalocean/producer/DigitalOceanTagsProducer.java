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
package org.apache.camel.component.digitalocean.producer;

import com.myjeeva.digitalocean.exception.DigitalOceanException;
import com.myjeeva.digitalocean.exception.RequestUnsuccessfulException;
import com.myjeeva.digitalocean.pojo.Delete;
import com.myjeeva.digitalocean.pojo.Tag;
import com.myjeeva.digitalocean.pojo.Tags;
import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for Tags API.
 */
public class DigitalOceanTagsProducer extends DigitalOceanProducer {

    public DigitalOceanTagsProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws RequestUnsuccessfulException, DigitalOceanException {
        switch (determineOperation(exchange)) {
            case list:
                getTags(exchange);
                break;
            case create:
                createTag(exchange);
                break;
            case get:
                getTag(exchange);
                break;
            case delete:
                deleteTag(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }

    }

    private void createTag(Exchange exchange) throws RequestUnsuccessfulException, DigitalOceanException {
        String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }
        Tag tag = getEndpoint().getDigitalOceanClient().createTag(name);
        LOG.trace("Create Tag [{}] ", tag);
        exchange.getMessage().setBody(tag);
    }

    private void getTag(Exchange exchange) throws RequestUnsuccessfulException, DigitalOceanException {
        String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }
        Tag tag = getEndpoint().getDigitalOceanClient().getTag(name);
        LOG.trace("Tag [{}] ", tag);
        exchange.getMessage().setBody(tag);
    }

    private void getTags(Exchange exchange) throws RequestUnsuccessfulException, DigitalOceanException {
        Tags tags = getEndpoint().getDigitalOceanClient().getAvailableTags(configuration.getPage(), configuration.getPerPage());
        LOG.trace("All Tags : page {} / {} per page [{}] ", configuration.getPage(), configuration.getPerPage(),
                tags.getTags());
        exchange.getMessage().setBody(tags.getTags());
    }

    private void deleteTag(Exchange exchange) throws RequestUnsuccessfulException, DigitalOceanException {
        String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }
        Delete delete = getEndpoint().getDigitalOceanClient().deleteTag(name);
        LOG.trace("Delete Tag [{}] ", delete);
        exchange.getMessage().setBody(delete);
    }

}
