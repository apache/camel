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
package org.apache.camel.component.quickfixj;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.impl.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.Message;
import quickfix.SessionID;

public class QuickfixjEndpoint extends DefaultEndpoint implements
		QuickfixjEventListener, MultipleConsumersSupport {
	public static final String EVENT_CATEGORY_KEY = "EventCategory";
	public static final String SESSION_ID_KEY = "SessionID";
	public static final String MESSAGE_TYPE_KEY = "MessageType";
	public static final String DATA_DICTIONARY_KEY = "DataDictionary";

	private static final Logger LOG = LoggerFactory
			.getLogger(QuickfixjEndpoint.class);

	private SessionID sessionID;
	private final List<QuickfixjConsumer> consumers = new CopyOnWriteArrayList<QuickfixjConsumer>();
	private final QuickfixjEngine engine;

	public QuickfixjEndpoint(QuickfixjEngine engine, String uri, CamelContext context) {
		super(uri, context);
		this.engine = engine;
	}

	public SessionID getSessionID() {
		return sessionID;
	}

	public void setSessionID(SessionID sessionID) {
		this.sessionID = sessionID;
	}

	public Consumer createConsumer(Processor processor) throws Exception {
		LOG.info("Creating QuickFIX/J consumer: "
				+ (sessionID != null ? sessionID : "No Session")
				+ ", ExchangePattern=" + getExchangePattern());
		QuickfixjConsumer consumer = new QuickfixjConsumer(this, processor);
		consumers.add(consumer);
		return consumer;
	}

	public Producer createProducer() throws Exception {
		LOG.info("Creating QuickFIX/J producer: "
				+ (sessionID != null ? sessionID : "No Session"));
		if (isWildcarded()) {
			throw new ResolveEndpointFailedException(
					"Cannot create consumer on wildcarded session identifier: "
							+ sessionID);
		}
		return new QuickfixjProducer(this);
	}

	public boolean isSingleton() {
		return true;
	}

	public void onEvent(QuickfixjEventCategory eventCategory,
			SessionID sessionID, Message message) throws Exception {
		if (this.sessionID == null || isMatching(sessionID)) {
			for (QuickfixjConsumer consumer : consumers) {
				Exchange exchange = QuickfixjConverters.toExchange(this,
						sessionID, message, eventCategory);
				consumer.onExchange(exchange);
				if (exchange.getException() != null) {
					throw exchange.getException();
				}
			}
		}
	}

	private boolean isMatching(SessionID sessionID) {
		return this.sessionID.equals(sessionID)
				|| (isMatching(this.sessionID.getBeginString(),
						sessionID.getBeginString())
						&& isMatching(this.sessionID.getSenderCompID(),
								sessionID.getSenderCompID())
						&& isMatching(this.sessionID.getSenderSubID(),
								sessionID.getSenderSubID())
						&& isMatching(this.sessionID.getSenderLocationID(),
								sessionID.getSenderLocationID())
						&& isMatching(this.sessionID.getTargetCompID(),
								sessionID.getTargetCompID())
						&& isMatching(this.sessionID.getTargetSubID(),
								sessionID.getTargetSubID()) && isMatching(
						this.sessionID.getTargetLocationID(),
						sessionID.getTargetLocationID()));
	}

	private boolean isMatching(String s1, String s2) {
		return s1.equals("") || s1.equals("*") || s1.equals(s2);
	}

	private boolean isWildcarded() {
		return sessionID != null
				&& (sessionID.getBeginString().equals("*")
						|| sessionID.getSenderCompID().equals("*")
						|| sessionID.getSenderSubID().equals("*")
						|| sessionID.getSenderLocationID().equals("*")
						|| sessionID.getTargetCompID().equals("*")
						|| sessionID.getTargetSubID().equals("*") || sessionID
						.getTargetLocationID().equals("*"));
	}

	public boolean isMultipleConsumersSupported() {
		return true;
	}

	public QuickfixjEngine getEngine() {
		return engine;
	}
	
	@Override
	protected void doStop() throws Exception {
		// clear list of consumers
		consumers.clear();
	}
}
