package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ByteBuffer;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;

/**
 * To test InOut exchange for the UDP protocol.
 */
public class MinaUdpWithInOutUsingPlainSocketTest extends ContextTestSupport {

    private static final transient Log LOG = LogFactory.getLog(MinaUdpWithInOutUsingPlainSocketTest.class);
    private static final int PORT = 4445;

    public void testSendAndReceiveOnce() throws Exception {
        String out = sendAndReceiveUdpMessages("World");
        assertNotNull("should receive data", out);
        assertEquals("Hello World", out);
    }

    private String sendAndReceiveUdpMessages(String input) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("127.0.0.1");

        byte[] data = input.getBytes();

        DatagramPacket packet = new DatagramPacket(data, data.length, address, PORT);
        LOG.debug("Sending data");
        socket.send(packet);

        Thread.sleep(1000);

        byte[] buf = new byte[128];
        DatagramPacket receive = new DatagramPacket(buf, buf.length, address, PORT);
        LOG.debug("Receving data");
        socket.receive(receive);

        socket.close();

        return new String(receive.getData(), 0, receive.getLength());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:udp://127.0.0.1:" + PORT + "?sync=true").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        ByteBuffer in = exchange.getIn().getBody(ByteBuffer.class);
                        String s = MinaConverter.toString(in);
                        exchange.getOut().setBody("Hello " + s);
                    }
                });
            }
        };
    }
    
}
