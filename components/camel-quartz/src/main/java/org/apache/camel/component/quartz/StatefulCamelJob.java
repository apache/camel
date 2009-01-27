/**
 *
 */
package org.apache.camel.component.quartz;

import org.apache.camel.CamelContext;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.StatefulJob;

/**
 * @author martin.gilday
 *
 */
public class StatefulCamelJob implements StatefulJob {

	/* (non-Javadoc)
	 * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
	 */
	public void execute(final JobExecutionContext context) throws JobExecutionException {

		SchedulerContext schedulerContext;
		try {
			schedulerContext = context.getScheduler().getContext();
		}
		catch (SchedulerException e) {
			throw new JobExecutionException("Failed to obtain scheduler context for job " + context.getJobDetail().getName());
		}

		CamelContext camelContext = (CamelContext) schedulerContext.get(QuartzEndpoint.CONTEXT_KEY);
		String endpointUri = (String) context.getJobDetail().getJobDataMap().get(QuartzEndpoint.ENDPOINT_KEY);
		QuartzEndpoint quartzEndpoint =	(QuartzEndpoint) camelContext.getEndpoint(endpointUri);
		quartzEndpoint.onJobExecute(context);
	}

}
