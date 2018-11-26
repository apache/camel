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
package org.apache.camel.component;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a ChatScript endpoint.
 */
@UriEndpoint(firstVersion = "2.22.0", scheme = "chatscript", title = "ChatScript", syntax = "chatscript:hostname:port/botname", consumerClass = ChatScriptConsumer.class, label = "ai,chatscript")
public class ChatScriptEndpoint extends DefaultEndpoint {
	
    public static final int DEFAULT_PORT = 1024;
    private final char nullChar = (char) 0;
    private final String CHAT_INIT=nullChar + "1" + nullChar + nullChar;
   
    private static final String URI_ERROR = "Invalid URI. Format must be of the form chatscript://hostname[:port]/botname?[options...]";
    @UriPath @Metadata(required = "true")
    private String hostname;
    @UriPath(defaultValue = "" + DEFAULT_PORT)
    private int port;
    @UriPath @Metadata(required = "true")
    private String botname;
    
    @UriParam(label = "username",defaultValue="camel")
    private String chatusername;
    @UriParam(label = "reset",defaultValue="false")
    private boolean resetchat;
    
    public boolean isResetchat() {
		return resetchat;
	}

	public void setResetchat(boolean resetchat) {
		this.resetchat = resetchat;
	}

	public String getChatusername() {
		return chatusername;
	}

	public void setChatusername(String chatusername) {
		this.chatusername = chatusername;
	}

	public ChatScriptEndpoint() {
    }

    public ChatScriptEndpoint(String uri,String remaining,
    		ChatScriptComponent component) throws URISyntaxException {
        super(uri, component);
        
        URI remainingUri = new URI("tcp://" + remaining);
      port = remainingUri.getPort() == -1 ? DEFAULT_PORT : remainingUri.getPort();
      if (remainingUri.getPath() == null || remainingUri.getPath().trim().length() == 0) {
          throw new IllegalArgumentException(URI_ERROR);
      }
          hostname = remainingUri.getHost();
          if (hostname == null) {
              throw new IllegalArgumentException(URI_ERROR);
          }
          botname=remainingUri.getPath();
              if (botname== null) {
                  throw new IllegalArgumentException(URI_ERROR);
              }
          botname=botname.substring(1);   
    
}
    public ChatScriptEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new ChatScriptProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new ChatScriptConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getBotname() {
		return botname;
	}

	public void setBotname(String botname) {
		this.botname = botname;
	}

	public static int getDefaultPort() {
		return DEFAULT_PORT;
	}

	public String getCHAT_INIT() {
		return CHAT_INIT;
	}
}
