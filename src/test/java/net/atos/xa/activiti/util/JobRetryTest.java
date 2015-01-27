package net.atos.xa.activiti.util;

import org.activiti.engine.impl.test.PluggableActivitiTestCase;
import org.activiti.engine.test.Deployment;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("classpath:activiti.cfg.xml")
public class JobRetryTest extends PluggableActivitiTestCase {

	@Test
	@Deployment(resources = { "diagrams/jobretry/JobRetry.bpmn" })
	public void testTryAgain() {
		JobRetry jobRetry = new JobRetry(processEngine);
		String processInstanceId = runtimeService.startProcessInstanceByKey("JobRetry").getId();
		assertEquals(0, FailingServiceTask.count);
		waitForJobExecutorToProcessAllJobs(15000, 100);
		assertEquals(2, FailingServiceTask.count);
		jobRetry.setRetries(processInstanceId, 1);
		waitForJobExecutorToProcessAllJobs(8000, 100);
		assertEquals(3, FailingServiceTask.count);
	}

}
