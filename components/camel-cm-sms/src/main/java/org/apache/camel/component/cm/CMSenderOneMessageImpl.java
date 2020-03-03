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
package org.apache.camel.component.cm;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.apache.camel.component.cm.exceptions.CMDirectException;
import org.apache.camel.component.cm.exceptions.XMLConstructionException;
import org.apache.camel.component.cm.exceptions.cmresponse.CMResponseException;
import org.apache.camel.component.cm.exceptions.cmresponse.InsufficientBalanceException;
import org.apache.camel.component.cm.exceptions.cmresponse.InvalidProductTokenException;
import org.apache.camel.component.cm.exceptions.cmresponse.NoAccountFoundForProductTokenException;
import org.apache.camel.component.cm.exceptions.cmresponse.UnknownErrorException;
import org.apache.camel.component.cm.exceptions.cmresponse.UnroutableMessageException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CMSenderOneMessageImpl implements CMSender {

    private static final Logger LOG = LoggerFactory.getLogger(CMSenderOneMessageImpl.class);

    private final String url;
    private final UUID productToken;

    public CMSenderOneMessageImpl(final String url, final UUID productToken) {

        this.url = url;
        this.productToken = productToken;
    }

    /**
     * Sends a message to CM endpoints. 1. CMMessage instance is going to be marshalled to xml. 2. Post request xml string to CMEndpoint.
     */
    @Override
    public void send(final CMMessage cmMessage) {

        // See: Check https://dashboard.onlinesmsgateway.com/docs for responses

        // 1.Construct XML. Throws XMLConstructionException
        final String xml = createXml(cmMessage);

        // 2. Try to send to CM SMS Provider ...Throws CMResponseException
        doHttpPost(url, xml);
    }

    private String createXml(final CMMessage message) {

        try {

            final ByteArrayOutputStream xml = new ByteArrayOutputStream();
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);

            // Get the DocumentBuilder
            final DocumentBuilder docBuilder = factory.newDocumentBuilder();

            // Create blank DOM Document
            final DOMImplementation impl = docBuilder.getDOMImplementation();
            final Document doc = impl.createDocument(null, "MESSAGES", null);

            // ROOT Element es MESSAGES
            final Element root = doc.getDocumentElement();

            // AUTHENTICATION element
            final Element authenticationElement = doc.createElement("AUTHENTICATION");
            final Element productTokenElement = doc.createElement("PRODUCTTOKEN");
            authenticationElement.appendChild(productTokenElement);
            final Text productTokenValue = doc.createTextNode("" + productToken);
            productTokenElement.appendChild(productTokenValue);
            root.appendChild(authenticationElement);

            // MSG Element
            final Element msgElement = doc.createElement("MSG");
            root.appendChild(msgElement);

            // <FROM>VALUE</FROM>
            final Element fromElement = doc.createElement("FROM");
            fromElement.appendChild(doc.createTextNode(message.getSender()));
            msgElement.appendChild(fromElement);

            // <BODY>VALUE</BODY>
            final Element bodyElement = doc.createElement("BODY");
            bodyElement.appendChild(doc.createTextNode(message.getMessage()));
            msgElement.appendChild(bodyElement);

            // <TO>VALUE</TO>
            final Element toElement = doc.createElement("TO");
            toElement.appendChild(doc.createTextNode(message.getPhoneNumber()));
            msgElement.appendChild(toElement);

            // <DCS>VALUE</DCS> - if UNICODE - messageOut.isGSM338Enc
            // false
            if (message.isUnicode()) {
                final Element dcsElement = doc.createElement("DCS");
                dcsElement.appendChild(doc.createTextNode("8"));
                msgElement.appendChild(dcsElement);
            }

            // <REFERENCE>VALUE</REFERENCE> -Alfanum
            final String id = message.getIdAsString();
            if (id != null && !id.isEmpty()) {
                final Element refElement = doc.createElement("REFERENCE");
                refElement.appendChild(doc.createTextNode("" + message.getIdAsString()));
                msgElement.appendChild(refElement);
            }

            // <MINIMUMNUMBEROFMESSAGEPARTS>1</MINIMUMNUMBEROFMESSAGEPARTS>
            // <MAXIMUMNUMBEROFMESSAGEPARTS>8</MAXIMUMNUMBEROFMESSAGEPARTS>
            if (message.isMultipart()) {
                final Element minMessagePartsElement = doc.createElement("MINIMUMNUMBEROFMESSAGEPARTS");
                minMessagePartsElement.appendChild(doc.createTextNode("1"));
                msgElement.appendChild(minMessagePartsElement);

                final Element maxMessagePartsElement = doc.createElement("MAXIMUMNUMBEROFMESSAGEPARTS");
                maxMessagePartsElement.appendChild(doc.createTextNode(Integer.toString(message.getMultiparts())));
                msgElement.appendChild(maxMessagePartsElement);
            }

            // Creatate XML as String
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
            final Transformer aTransformer = transformerFactory.newTransformer();
            aTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            final Source src = new DOMSource(doc);
            final Result dest = new StreamResult(xml);
            aTransformer.transform(src, dest);
            return xml.toString();
        } catch (final TransformerException e) {
            throw new XMLConstructionException(String.format("Cant serialize CMMessage %s", message), e);
        } catch (final ParserConfigurationException e) {
            throw new XMLConstructionException(String.format("Cant serialize CMMessage %s", message), e);
        }
    }

    private void doHttpPost(final String urlString, final String requestString) {

        final HttpClient client = HttpClientBuilder.create().build();
        final HttpPost post = new HttpPost(urlString);
        post.setEntity(new StringEntity(requestString, Charset.forName("UTF-8")));

        try {

            final HttpResponse response = client.execute(post);

            final int statusCode = response.getStatusLine().getStatusCode();

            LOG.debug("Response Code : {}", statusCode);

            if (statusCode == 400) {
                throw new CMDirectException("CM Component and CM API show some kind of inconsistency. "
                                            + "CM is complaining about not using a post method for the request. And this component only uses POST requests. What happens?");
            }

            if (statusCode != 200) {
                throw new CMDirectException("CM Component and CM API show some kind of inconsistency. The component expects the status code to be 200 or 400. New api released? ");
            }

            // So we have 200 status code...

            // The response type is 'text/plain' and contains the actual
            // result of the request processing.

            // We obtaing the result text
            try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final StringBuffer result = new StringBuffer();
                String line = null;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }

                // ... and process it

                line = result.toString();
                if (!line.isEmpty()) {

                    // Line is not empty = error
                    LOG.debug("Result of the request processing: FAILED\n{}", line);

                    // The response text contains the error description. We will
                    // throw a custom exception for each.

                    if (line.contains(CMConstants.ERROR_UNKNOWN)) {
                        throw new UnknownErrorException();
                    } else if (line.contains(CMConstants.ERROR_NO_ACCOUNT)) {
                        throw new NoAccountFoundForProductTokenException();
                    } else if (line.contains(CMConstants.ERROR_INSUFICIENT_BALANCE)) {
                        throw new InsufficientBalanceException();
                    } else if (line.contains(CMConstants.ERROR_UNROUTABLE_MESSAGE)) {
                        throw new UnroutableMessageException();
                    } else if (line.contains(CMConstants.ERROR_INVALID_PRODUCT_TOKEN)) {
                        throw new InvalidProductTokenException();
                    } else {

                        // SO FAR i would expect other kind of ERROR.

                        // MSISDN correctness and message validity is client
                        // responsibility
                        throw new CMResponseException("CHECK ME. I am not expecting this. ");
                    }
                }

                // Ok. Line is EMPTY - successfully submitted
                LOG.debug("Result of the request processing: Successfully submited");
            }
        } catch (final IOException io) {
            throw new CMDirectException(io);
        } catch (Throwable t) {
            if (!(t instanceof CMDirectException)) {
                // Chain it
                t = new CMDirectException(t);
            }
            throw (CMDirectException) t;
        }
    }
}
