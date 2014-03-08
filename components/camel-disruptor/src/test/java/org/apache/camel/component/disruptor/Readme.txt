Seda and Disruptor unit tests comparaison
-----------------------------------------

The Seda Camel component has a lot of similarities with the Disruptor and it 
just seems fair to copy the unit tests from Seda and apply them to the Disruptor.  
Here is a list of all the unit tests found in Camel 2.10.1 and their correspondence:

Original Camel Test class						Status			Status extra information
-------------------------						------			------------------------ 
CollectionProducerTest.java						*NOT COPIED*	Did not really apply to disruptor
DirectRequestReplyAndSedaInOnlyTest.java		*COPIED*		DirectRequestReplyAndDisruptorInOnlyTest.java
FileSedaShutdownCompleteAllTasksTest.java		*COPIED*		FileDisruptorShutdownCompleteAllTasksTest.java
SedaAsyncProducerTest.java						*NOT COPIED*	Test case doesn't have any relationhsip with disruptor, only applies to old seda implementation
SedaAsyncRouteTest.java							*COPIED*		DisruptorAsyncRouteTest.java
SedaBlockWhenFullTest.java						*COPIED*		DisruptorBlockWhenFullTest.java
SedaComplexInOutTest.java						*COPIED*		DisruptorComplexInOutTest.java
SedaComponentReferenceEndpointTest.java			*COPIED*		DisruptorComponentReferenceEndpointTest.java
SedaConcurrentConsumersNPEIssueTest.java		*COPIED*		DisruptorConcurrentConsumersNPEIssueTest.java
SedaConcurrentConsumersTest.java				*COPIED*		DisruptorConcurrentConsumersTest.java
SedaConcurrentTest.java							*COPIED*		DisruptorConcurrentTest.java
SedaConfigureTest.java							*COPIED*		DisruptorConfigureTest.java
SedaConsumerSuspendResumeTest.java				*COPIED*		DisruptorConsumerSuspendResumeTest.java
SedaDefaultUnboundedQueueSizeTest.java			*NOT COPIED*	Disruptor doesn't support an upper limit and waits by default when ring buffer is full
SedaEndpointTest.java							*NOT COPIED*	Test case did not seem fit for disruptor.  Could be revisited if needed
SedaFromRouteIdTest.java						*COPIED*		DisruptorFromRouteIdTest.java
SedaInOnlyChainedTest.java						*COPIED*		DisruptorInOnlyChainedTest.java
SedaInOnlyTest.java								*COPIED*		DisruptorInOnlyTest.java
SedaInOutBigChainedTest.java					*COPIED*		DisruptorInOutBigChainedTest.java
SedaInOutChainedTest.java						*COPIED*		DisruptorInOutChainedTest.java
SedaInOutChainedTimeoutTest.java				*COPIED*		DisruptorInOutChainedTimeoutTest.java
SedaInOutChainedWithOnCompletionTest.java		*COPIED*		DisruptorInOutChainedWithOnCompletionTest.java
SedaInOutTest.java								*COPIED*		DisruptorInOutTest.java
SedaInOutWithErrorDeadLetterChannelTest.java	*COPIED*		DisruptorInOutWithErrorDeadLetterChannelTest.java
SedaInOutWithErrorTest.java						*COPIED*		DisruptorInOutWithErrorTest.java
SedaMultipleConsumersTest.java					*COPIED*		DisruptorMultipleConsumersTest.java
SedaNoConsumerTest.java							*COPIED*		DisruptorNoConsumerTest.java
SedaQueueTest.java								*COPIED*		DisruptorRingBufferTest.java
SedaRemoveRouteThenAddAgainTest.java			*COPIED*		DisruptorRemoveRouteThenAddAgainTest.java			
SedaRouteTest.java								*COPIED*		DisruptorRouteTest.java								
SedaShouldNotUseSameThreadTest.java				*COPIED*		DisruptorShouldNotUseSameThreadTest.java
SedaTimeoutDisabledTest.java					*COPIED*		DisruptorTimeoutDisabledTest.java
SedaTimeoutTest.java							*COPIED*		DisruptorTimeoutTest.java
SedaUnitOfWorkTest.java							*COPIED*		DisruptorUnitOfWorkTest.java
SedaWaitForTaskAsPropertyTest.java				*COPIED*		DisruptorWaitForTaskAsPropertyTest.java
SedaWaitForTaskCompleteOnCompletionTest.java	*COPIED*		DisruptorWaitForTaskCompleteOnCompletionTest.java
SedaWaitForTaskCompleteTest.java				*COPIED*		DisruptorWaitForTaskCompleteTest.java
SedaWaitForTaskIfReplyExpectedTest.java			*COPIED*		DisruptorWaitForTaskIfReplyExpectedTest.java
SedaWaitForTaskNewerOnCompletionTest.java		*COPIED*		DisruptorWaitForTaskNeverOnCompletionTest.java
SedaWaitForTaskNewerTest.java					*COPIED*		DisruptorWaitForTaskNeverTest.java
TracingWithDelayTest.java						*NOT COPIED*	No direct association with disruptor
