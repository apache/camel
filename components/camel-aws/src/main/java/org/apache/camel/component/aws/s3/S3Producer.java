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
package org.apache.camel.component.aws.s3;

import java.io.InputStream;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage Service
 * <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class S3Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(S3Producer.class);

    public S3Producer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                getConfiguration().getBucketName(),
                determineKey(exchange),
                exchange.getIn().getMandatoryBody(InputStream.class),
                objectMetadata);
        
        LOG.trace("Put object [{}] from exchange [{}]...", putObjectRequest, exchange);
        
        PutObjectResult putObjectResult = getEndpoint().getS3Client().putObject(putObjectRequest);

        LOG.trace("Received result [{}]", putObjectResult);
        
        Message message = getMessageForResponse(exchange);
        message.setHeader(S3Constants.E_TAG, putObjectResult.getETag());
        if (putObjectResult.getVersionId() != null) {
            message.setHeader(S3Constants.VERSION_ID, putObjectResult.getVersionId());            
        }
    }
    
    private String determineKey(Exchange exchange) {
        String key = exchange.getIn().getHeader(S3Constants.KEY, String.class);
        if (key == null) {
            throw new IllegalArgumentException("AWS S3 Key header missing.");
        }
        return key;
    }

    private Message getMessageForResponse(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        
        return exchange.getIn();
    }
    
    protected S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    @Override
    public String toString() {
        return "S3Producer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }
    
    @Override
    public S3Endpoint getEndpoint() {
        return (S3Endpoint) super.getEndpoint();
    }
}