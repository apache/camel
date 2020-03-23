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
package org.apache.camel.component.aws.s3.client.impl;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3EncryptionClientBuilder;
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider;
import org.apache.camel.component.aws.s3.S3Configuration;
import org.apache.camel.component.aws.s3.client.S3Client;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage an AWS s3 client for all users to use (enabling temporary creds). This
 * implementation is for remote instances to manage the credentials on their own
 * (eliminating credential rotations)
 */
public class S3ClientIAMOptimizedImpl implements S3Client {
    private static final Logger LOG = LoggerFactory.getLogger(S3ClientIAMOptimizedImpl.class);
    private S3Configuration configuration;
    private int maxConnections;

    /**
     * Constructor that uses the config file.
     */
    public S3ClientIAMOptimizedImpl(S3Configuration configuration, int maxConnections) {
        LOG.trace("Creating an AWS S3 client for an ec2 instance with IAM temporary credentials (normal for ec2s).");
        this.configuration = configuration;
        this.maxConnections = maxConnections;
    }

    /**
     * Getting the s3 aws client that is used.
     * 
     * @return Amazon S3 Client.
     */
    @Override
    public AmazonS3 getS3Client() {
        AmazonS3 client = null;
        AmazonS3ClientBuilder clientBuilder = null;
        AmazonS3EncryptionClientBuilder encClientBuilder = null;

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setMaxConnections(maxConnections);

        if (configuration.hasProxyConfiguration()) {
            clientConfiguration.setProxyProtocol(configuration.getProxyProtocol());
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
        }

        if (configuration.getAccessKey() != null || configuration.getSecretKey() != null) {
            LOG.trace("Do not pass in unnecessary static credentials when selecting the IAM credential option.");
        }

        if (!configuration.isUseEncryption()) {
            clientBuilder = AmazonS3ClientBuilder.standard().withCredentials(new InstanceProfileCredentialsProvider(false));
        } else if (configuration.isUseEncryption()) {
            StaticEncryptionMaterialsProvider encryptionMaterialsProvider = new StaticEncryptionMaterialsProvider(configuration.getEncryptionMaterials());
            encClientBuilder = AmazonS3EncryptionClientBuilder.standard().withClientConfiguration(clientConfiguration).withEncryptionMaterials(encryptionMaterialsProvider)
                .withCredentials(new InstanceProfileCredentialsProvider(false));
        } else {
            clientBuilder = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(new InstanceProfileCredentialsProvider(false));
        }

        if (!configuration.isUseEncryption()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                clientBuilder = clientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
            }
            clientBuilder = clientBuilder.withPathStyleAccessEnabled(configuration.isPathStyleAccess());
            if (ObjectHelper.isNotEmpty(configuration.getEndpointConfiguration())) {
                clientBuilder.withEndpointConfiguration(configuration.getEndpointConfiguration());
            }
            client = clientBuilder.build();
        } else {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                encClientBuilder = encClientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
            }
            encClientBuilder = encClientBuilder.withPathStyleAccessEnabled(configuration.isPathStyleAccess());
            if (ObjectHelper.isNotEmpty(configuration.getEndpointConfiguration())) {
                encClientBuilder.withEndpointConfiguration(configuration.getEndpointConfiguration());
            }
            client = encClientBuilder.build();
        }

        return client;
    }
}
