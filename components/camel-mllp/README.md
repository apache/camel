# camel-mllp
Camel MLLP Component

See MLLP Specification here:
http://www.hl7.org/documentcenter/public_temp_E7494E36-1C23-BA17-0C5E72EF77542E1F/wg/inm/mllp_transport_specification.PDF

# Description
The camel-mllp component is specifically designed to handle the nuances of the MLLP protocol and provide
the functionality required by Healthcare providers to communicate with other systems using the MLLP protocol.

The component uses byte[] payloads, and relies on the Camel type conversion system for converting other payload
types to/from byte[].  This allows using other HL7 Libraries (i.e. HAPI) to parse the messages.  The component
provides a 'charset' URI option that will cause the endpoint to set the CamelCharsetName property on the exchange
which allows the proper conversion of byte[] to String payloads for Consumers.

The component provides a simple URI for configuring MLLP endpoints:
MLLP-Producers:
  to( "mllp://<host or ip>:<port>" )
MLLP-Consumers:
  from("mllp://<host or ip>:<listening port>) to listen on a specific address
  from("mllp://port") to listen on all local addresses

MLLP-Producers also interrogate the HL7 Acknowledgment received from the external system and if a negative acknowledgment
is received, the producer sets an exception on the exchange indicating the type of negative acknowledgement that was
received (i.e. a HL7 Application Reject Acknowledgement, Application Error Acknowledgement, 
Commit Reject Acknowledgement and Commit Error Acknowledgement).  This enables the use of Camel Redelivery 
Policies to configure redelivery attempts and routing erroneous messages to alternate endpoints for analysis.

MLLP-Consumers will, by default, automatically generate an acknowledgement.  A HL7 Application Accept Acknowledgment 
will be generated for successfully processed messages, or a HL7 Application Error Acknowledgement for messages where an 
exception is raised during the processing of the exchange.  The HL7 acknowledgement can also be specified by setting the 
CamelMllpAcknowledgement property on the exchange - if present, the value of this property will be used for the HL7
acknowledgment.  The automatic generation of an HL7 acknowledgment can be completely disabled by setting the autoAck
URI parameter to false.  If autoAck=false and the CamelMllpAcknowledgment property is not set on the exchange, and 
exception will be raised.

The component also provides a Camel Processor that is capable of generating HL7 Acknowledgements.  Therefore, the HAPI 
is not required to generate HL7 Acknowledgements - however, it can be used if desired.
By default, the processor will generate a HL7 Application Accept Acknowledgement if there is not an exception on the 
Camel Exchange - otherwise it will generate a HL7 Application Error Acknowledgement.  The generated acknowledgement 
is placed in the CamelMllpAcknowledgment property on the Exchange.

Regardless of whether the HL7 Acknowledgment is generated or specified using the CamelMllpAcknowledgement Exchange property,
the MLLP-Consumer will set the CamelHllpAcknowledgement and CamelHllpAcknowledgementCode headers on the Message after the
acknowledgment is successfully transmitted to the external system. 

Since the MLLP protocol does not typically use a large number of concurrent connections, the camel-mllp component uses
a simple thread-per-connection model based an standard Java Sockets.  This keeps the implementation simple, and also
eliminates the dependencies on other camel components.
 
# Rationalization
While Camel already includes some of this functionality in the camel-hl7 component, the implementation does not
provide all of the functionality required to effectively handle the MLLP protocol - especially under adverse conditions.
  
The camel-hl7 Mina2 codec and Netty4 decoder do not handle MLLP Framing errors very well - the component will hang waiting
for frames to complete in some instances.

While both camel-mina2 and camel-netty4 provide a "timeout" function, it is only applied to Producers.  MLLP Consumers
also need to be able to timeout to recover from MLLP framing errors.  Additionally, the timeout functionality of the
camel-netty4 component is disable after any data is received on the connection, making in ineffective for detecting
timeouts after the first messages is received.

Also, neither the Mina2 codec nor the Netty4 decoder interrogate HL7 Acknowledgments.  Therefore, it is much more
difficult to use the redelivery and error handling features Camel provides.

The above issues may be addressable by updating/patching the existing components, but there is one more that is not.
Both camel-netty4 and camel-mina2 are designed to handle a large number of concurrent connections, rapid connect/disconnect
rates, and asynchronous communication.  Forcing, these components to deal with the small number of stateful connections
inherent to the MLLP protocol seems inappropriate.

An attempt was made to update the camel-netty4 decoder provided by the camel-hl7 component to deal with the nuances of 
the MLLP protocol, but it quickly became very complicated.  The decoder was updated to correctly deal with the MLLP 
frame.  The current implementation is based on the Netty DelimiterBasedFrameDecoder, but this decoder only looks for a 
single byte terminator and MLLP uses two bytes to terminate it's frame.  Additionally, the second terminating byte of the
MLLP frame can't be used alone because that byte is also contained in HL7 messages (it is the HL7 Segment Delimiter).
An implementation resembling the Netty LineBasedFrameDecoder was written at it correctly handled the MLLP framing issues,
but the timout issues were never addressed.

For MLLP Consumers, the decoder needs to dynamically install a timeout handler whenver a partial MLLP frame is received, 
and then remove it when the frame is completed.  For MLLP Producers, the decoder would need to install a timeout handler 
at some point to enable detecting a missing acknowledgement in addition to the timeout handler to deal with the 
partial/incomplete acknowledgement.

# MLLP Background
The MLLP protocol is inherently synchronous because external systems almost always require the order of messages to be
maintained (i.e. FIFO delivery).

When a MLLP-Producer sends a message to an external system, it is required to wait for 
an HL7 Acknowledgement before sending the next message.  Additionally, the content of the acknowlegement must be examined
to determine the specific type of the acknowlegement before the next message can be transmitted.  If the acknowledgement 
is a HL7 Application Error Acknowledgement, the MLLP-Producer should retransmit the message a few times (the number of 
redelivery attempts is application specific).  If the acknowledgment is a HL7 Application Reject Acknowledgement, there 
is something wrong with the message and redelivery will never succeed.  The HL7 Messages acknowledged with an HL7 Application
Reject Acknowledgement must be routed to an alternate destination to allow users to investigate the nature of the error
so it can be corrected.

When a MLLP-Consumer receives a message from an external system, the sending system is required to wait for an HL7 Acknowledgement
before transmitting the next message.  The MLLP-Consumer normally persists the message in a durable store and then replies
to the sending system with a HL7 Application Accept Acknowledgement.  If a transient error occurs while persisting the
message, the MLLP-Consumer should reply with a HL7 Application Error Acknowledgment and allow the external system to resend
the message.  If the MLLP-Consumer detects the that the received message is invalid for some reason and the message could
never be processed, it should reply with an HL7 Application Reject Acknowledgement and the sending system should not
attempt to resend the message.

NOTE:  Some external systems do not handle HL7 NACKS ( HL7 Application Reject Acknowledgments and HL7 Application Error
Acknowledgements) - they do not interrogate the HL7 Acknowledgment to determine if it is a negative acknowledgement and 
assume any acknowledgement received is an HL7 ACK (HL7 Application Accept Acknowledgement).  In order to prevent message 
loss when dealing with external systems that behave in this fashion, the MLLP-Consumer must be capable of closing the 
TCP connection in lew of sending and HL7 NACK, which will force the external system to resend the message.  Additionally,
the MLLP-Consumer may be required to behave differently for each type of HL7 NACK - it may need to close the TCP connection
instead of sending HL7 Application Error Acknowledgements, and route the messages that would be normally not be persisted 
to an alternate durable store before sending the the HL7 Application Reject acknowledgement.

Systems using the MLLP protocol normally use stateful TCP connections - the connections are established and left open 
for extended periods of time.

A MLLP-Consumer endpoint may have more than one TCP connection at a given time, but this is not the typical case.  Normally
there is a single active TCP connection to a MLLP-Consumer.

A MLLP-Producer endpoint should only have a single TCP connection at any given time. If the producer attempts to open more
than one connection to an external system, it oftentimes causes issues with the external system.  Additionally, since FIFO
must be maintained, only 

