package org.apache.camel.component.mllp;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * The mllp producer.
 */
public class MllpTcpClientProducer extends DefaultProducer {

    // Logger log = LoggerFactory.getLogger(this.getClass());

    private MllpEndpoint endpoint;

    Socket socket;
    BufferedOutputStream outputStream;
    InputStream inputStream;

    public MllpTcpClientProducer(MllpEndpoint endpoint) throws SocketException {
        super(endpoint);
        log.trace("MllpTcpClientProducer(endpoint)");

        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        log.trace("doStart()");

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.trace("doStop()");

        if (null != outputStream) {
            try {
                outputStream.close();
            } finally {
                outputStream = null;
            }
        }

        if (null != inputStream) {
            try {
                inputStream.close();
            } catch (IOException ioEx) {
                log.warn("Exception encountered closing the input stream for the client socket", ioEx);
            } finally {
                inputStream = null;
            }
        }

        if (null != socket) {
            if (!socket.isClosed()) {
                try {
                    socket.shutdownInput();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered shutting down the input stream on the client socket", ioEx);
                }

                try {
                    socket.shutdownOutput();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered shutting down the output stream on the client socket", ioEx);
                }

                try {
                    socket.close();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered closing the client socket", ioEx);
                }
            }
            socket = null;
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        log.trace("doSuspend()");

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        log.trace("doResume()");

        super.doSuspend();
    }

    @Override
    protected void doShutdown() throws Exception {
        log.trace("doShutdown()");

        super.doShutdown();
    }

    public void process(Exchange exchange) throws Exception {
        log.trace("process(exchange)");

        checkConnection();

        String hl7Message = exchange.getOut().getBody(String.class);
        if (null == hl7Message) {
            hl7Message = exchange.getIn().getBody(String.class);
            log.debug("Processing message from 'inputStream' message");
        } else {
            log.debug("Processing message from 'outputStream' message");
        }

        byte[] hl7Bytes = hl7Message.getBytes(endpoint.charset);

        outputStream.write(MllpEndpoint.START_OF_BLOCK);
        outputStream.write(hl7Bytes, 0, hl7Bytes.length);
        outputStream.write(MllpEndpoint.END_OF_BLOCK);
        outputStream.write(MllpEndpoint.END_OF_DATA);
        outputStream.flush();

        StringBuilder acknowledgementBuilder = new StringBuilder();

        try {
            int inByte = inputStream.read();
            if ( inByte != MllpEndpoint.START_OF_BLOCK) {
                // We have out-of-band data
                StringBuilder outOfBandData = new StringBuilder();
                do {
                    outOfBandData.append((char)inByte);
                    inByte = inputStream.read();
                } while ( MllpEndpoint.START_OF_BLOCK != inByte );
                log.warn( "Eating out-of-band data: {}", outOfBandData.toString());
            }

            if (MllpEndpoint.START_OF_BLOCK != inByte) {
                throw new MllpEnvelopeException("Message did not start with START_OF_BLOCK");
            }
            boolean readingMessage = true;
            while (readingMessage) {
                int nextByte = inputStream.read();
                switch (nextByte) {
                    case -1:
                        throw new MllpEnvelopeException("Reached end of stream before END_OF_BLOCK");
                    case MllpEndpoint.START_OF_BLOCK:
                        throw new MllpEnvelopeException("Received START_OF_BLOCK before END_OF_BLOCK");
                    case MllpEndpoint.END_OF_BLOCK:
                        if (MllpEndpoint.END_OF_DATA != inputStream.read()) {
                            throw new MllpEnvelopeException("END_OF_BLOCK was not followed by END_OF_DATA");
                        }
                        readingMessage = false;
                        break;
                    default:
                        acknowledgementBuilder.append((char) nextByte);
                }
            }
        } catch (SocketTimeoutException timeoutEx ) {
            log.error( "Timout reading response");
            throw new MllpResponseTimeoutException( "Timeout reading response", timeoutEx);
        } catch (IOException e) {
            log.error("Unable to read HL7 acknowledgement", e);
            throw new MllpEnvelopeException("Unable to read HL7 acknowledgement", e);
        }

        log.debug("Populating the exchange");

        exchange.getIn().setBody(acknowledgementBuilder.toString(), String.class);
    }

    void checkConnection() throws IOException {
        if (null == socket || socket.isClosed() || ! socket.isConnected() ) {
            socket = new Socket();

            socket.setKeepAlive(endpoint.keepAlive);
            socket.setTcpNoDelay(endpoint.tcpNoDelay);
            socket.setReceiveBufferSize( endpoint.receiveBufferSize );
            socket.setSendBufferSize( endpoint.sendBufferSize );
            socket.setReuseAddress(endpoint.reuseAddress);
            socket.setSoLinger(false, -1);

            // Read Timeout
            socket.setSoTimeout(endpoint.responseTimeout);

            log.debug("Connecting to socket on {}:{}", endpoint.getHostname(), endpoint.getPort());
            socket.connect(new InetSocketAddress(endpoint.getHostname(), endpoint.getPort()), endpoint.connectTimeout);
            outputStream = new BufferedOutputStream(socket.getOutputStream(), endpoint.sendBufferSize);
            inputStream = socket.getInputStream();
        }

    }
}
