package org.wordpress4j.service.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

import java.util.Collections;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wordpress4j.auth.WordpressAuthentication;
import org.wordpress4j.service.WordpressService;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

abstract class AbstractWordpressServiceAdapter<A> implements WordpressService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWordpressServiceAdapter.class);

    private A spi;

    private final String apiVersion;
    private WordpressAuthentication authentication;

    AbstractWordpressServiceAdapter(final String wordpressUrl, final String apiVersion) {
        checkNotNull(emptyToNull(apiVersion));
        this.apiVersion = apiVersion;

        //@formatter:off
        this.spi = JAXRSClientFactory.create(wordpressUrl, 
                                              this.getSpiType(), 
                                              Collections.singletonList(new JacksonJsonProvider()));
        //@formatter:on
        WebClient.client(spi).type(MediaType.APPLICATION_JSON_TYPE);
        WebClient.client(spi).accept(MediaType.APPLICATION_JSON_TYPE);
        
        // TODO: leave this kind of configuration to API clients
        WebClient.getConfig(spi).getHttpConduit().getClient().setAutoRedirect(true);

        /*
         * TODO: aggregate a configuration object to customize the JAXRS
         * behavior, eg.: adding handlers or interceptors
         */
        WebClient.getConfig(spi).getInInterceptors().add(new LoggingInInterceptor());
        WebClient.getConfig(spi).getOutInterceptors().add(new LoggingOutInterceptor());

        if (this.authentication != null) {
            this.authentication.configureAuthentication(spi);
        }

        LOGGER.info("******* {} API initialized *********", spi.getClass().getSimpleName());
    }

    protected abstract Class<A> getSpiType();

    protected final A getSpi() {
        return spi;
    }

    protected final String getApiVersion() {
        return this.apiVersion;
    }

    @Override
    public final void setWordpressAuthentication(WordpressAuthentication authentication) {
        this.authentication = authentication;
    }
}
