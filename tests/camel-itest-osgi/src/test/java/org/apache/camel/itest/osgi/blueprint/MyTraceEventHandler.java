package org.apache.camel.itest.osgi.blueprint;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.TraceEventHandler;
import org.apache.camel.processor.interceptor.TraceInterceptor;

import java.util.LinkedList;
import java.util.List;

public class MyTraceEventHandler implements TraceEventHandler {

    private List<StringBuilder> eventMessages;

    public MyTraceEventHandler() {
        this.eventMessages = new LinkedList<StringBuilder>();
    }

    public static void recordComplete(StringBuilder message, ProcessorDefinition<?> node, Exchange exchange) {
        message.append("Complete: ");
        message.append(node.getLabel() + ": ");
        message.append(exchange.getIn().getBody());
    }

    public static void recordIn(StringBuilder message, ProcessorDefinition<?> node, Exchange exchange) {
        message.append("In: ");
        message.append(node.getLabel() + ": ");
        message.append(exchange.getIn().getBody());
    }

    public static void recordOut(StringBuilder message, ProcessorDefinition<?> node, Exchange exchange) {
        message.append("Out: ");
        message.append(node.getLabel() + ": ");
        if (null != exchange.getOut()) {
            message.append(exchange.getOut().getBody());
        }
        if (null != exchange.getException()) {
            Exception ex = exchange.getException();
            message.append("\t");
            message.append("Ex: ");
            message.append(ex.getMessage());
        }
    }

    private synchronized void storeMessage(StringBuilder message) {
        eventMessages.add(message);
    }

    public void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
            StringBuilder message = new StringBuilder();
            recordComplete(message, node, exchange);
            storeMessage(message);
    }

    public Object traceExchangeIn(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
            StringBuilder message = new StringBuilder();
            recordIn(message, node, exchange);
            return message;
    }

    public void traceExchangeOut(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
            if (StringBuilder.class.equals(traceState.getClass())) {
                StringBuilder message = (StringBuilder) traceState;
                recordOut(message, node, exchange);
                storeMessage(message);
            }
    }
}
