/**
 * 
 */
package org.apache.camel.component.atmosphere.websocket;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;

/**
 * This servlet is used to add some websocket specific handling at the moment.
 * 
 * REVISIT
 * we might be able to get rid of this servlet by overriding some of the binding
 * code that is executed between the servlet and the consumer.
 * 
 */
public class CamelWebSocketServlet extends CamelHttpTransportServlet {
    private static final long serialVersionUID = 1764707448550670635L;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException,
        IOException {
        log.trace("Service: {}", request);

        // Is there a consumer registered for the request.
        HttpConsumer consumer = resolve(request);
        if (consumer == null) {
            log.debug("No consumer to service request {}", request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }       
        
        // are we suspended?
        if (consumer.isSuspended()) {
            log.debug("Consumer suspended, cannot service request {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        if (consumer.getEndpoint().getHttpMethodRestrict() != null 
            && !consumer.getEndpoint().getHttpMethodRestrict().equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if ("TRACE".equals(request.getMethod()) && !consumer.isTraceEnabled()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (!(consumer instanceof WebsocketConsumer)) {
            log.debug("Consumer not supporting websocket {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        log.debug("Dispatching to Websocket Consumer at {}", consumer.getPath());
        ((WebsocketConsumer)consumer).service(request, response);
    }
    
}
