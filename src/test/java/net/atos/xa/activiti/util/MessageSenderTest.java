package net.atos.xa.activiti.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.activiti.engine.test.Deployment;
import org.activiti.spring.impl.test.SpringActivitiTestCase;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("classpath:activiti.cfg.xml")
public class MessageSenderTest extends SpringActivitiTestCase {

	@Test
	@Deployment(resources = { "diagrams/messages/WaitMessage.bpmn" })
	public void testManually() {
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("a", 3);
		variables.put("b", 8);
		String businessKey = "BK" + new Random().nextLong();
		String processInstanceId = runtimeService.startProcessInstanceByKey(
				"WaitMessage", businessKey, variables).getId();
		// process started
		ExecutionQuery receiveTaskQueryRight = receiveTaskQuery(businessKey);
		ExecutionQuery receiveTaskQueryWrong = receiveTaskQuery("toto");
		TaskQuery taskQuery = taskService.createTaskQuery()
				.executionId(processInstanceId).taskDefinitionKey("usertask1");
		assertEquals(1, taskQuery.count());
		assertEquals(0, receiveTaskQueryRight.count());
		// user task ready, receive task not yet
		Task userTask1 = taskQuery.singleResult();
		taskService.complete(userTask1.getId());
		assertEquals(0, taskQuery.count());
		assertEquals(1, receiveTaskQueryRight.count());
		assertEquals(0, receiveTaskQueryWrong.count());
		// user task closed, receive task ready (only for correct business key)
		Execution receiveTask = receiveTaskQueryRight.singleResult();
		assertEquals(processInstanceId, receiveTask.getProcessInstanceId());
		runtimeService.setVariable(processInstanceId, "b", 7);
		// checked ability to modify variable in the right process instance
		TaskQuery taskQuery2 = taskService.createTaskQuery()
				.executionId(processInstanceId).taskDefinitionKey("usertask2");
		assertEquals(0, taskQuery2.count());
		runtimeService.signal(receiveTask.getId());
		assertEquals(1, taskQuery2.count());
		assertEquals(0, receiveTaskQueryRight.count());
		// checked completion of receive task

	}

	@Test
	@Deployment(resources = { "diagrams/messages/WaitMessage.bpmn" })
	public void testMessageSender() {
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("a", 3);
		variables.put("b", 8);
		String businessKey = "BK" + new Random().nextLong();
		String processInstanceId = runtimeService.startProcessInstanceByKey(
				"WaitMessage", businessKey, variables).getId();
		// process started
		ExecutionQuery receiveTaskQueryRight = receiveTaskQuery(businessKey);
		taskService.complete(taskService.createTaskQuery()
				.executionId(processInstanceId).taskDefinitionKey("usertask1")
				.singleResult().getId());
		// user task closed, receive task ready
		TaskQuery taskQuery2 = taskService.createTaskQuery()
				.executionId(processInstanceId).taskDefinitionKey("usertask2");
		assertEquals(0, taskQuery2.count());
		MessageSender messageSender = new MessageSender(processEngine);
		assertNull(messageSender.sendMessage(businessKey, "wrong task id",
				Collections.singletonMap("b", 7)));
		assertEquals(1, receiveTaskQueryRight.count());// still open
		String sendMessageId = messageSender.sendMessage(businessKey,
				"receivetask1", Collections.singletonMap("b", 7));
		assertEquals(processInstanceId, sendMessageId);
		assertEquals(7, runtimeService.getVariable(processInstanceId, "b"));
		// checked ability to modify variable in the right process instance
		assertEquals(1, taskQuery2.count());
		assertEquals(0, receiveTaskQueryRight.count());
		// checked completion of receive task

	}

	@Test
	@Deployment(resources = { "diagrams/messages/WaitMessage.bpmn",
			"diagrams/messages/ParentOfWaitMessage.bpmn" })
	public void testMessageSenderWithSubProcess() {
		String businessKey = "BK" + new Random().nextLong();
		runtimeService.startProcessInstanceByKey("ParentOfWaitMessage",
				businessKey).getId();
		// process started
		Queries queries = new Queries(processEngine);
		List<String> subProcesses = queries
				.recursiveSubprocessesByBusinessKey(businessKey);
		assertEquals(1, subProcesses.size());
		String subProcessId = subProcesses.get(0);
		ExecutionQuery receiveTaskQueryRight = receiveTaskQueryByInstanceId(subProcessId);
		taskService.complete(taskService.createTaskQuery()
				.executionId(subProcessId).taskDefinitionKey("usertask1")
				.singleResult().getId());
		// user task closed, receive task ready
		TaskQuery taskQuery2 = taskService.createTaskQuery()
				.executionId(subProcessId).taskDefinitionKey("usertask2");
		assertEquals(0, taskQuery2.count());
		MessageSender messageSender = new MessageSender(processEngine);
		assertNull(messageSender.sendMessageToSubProcess(businessKey,
				"wrong task id", Collections.singletonMap("b", 7)));
		assertEquals(1, receiveTaskQueryRight.count());// still open
		String sendMessageId = messageSender.sendMessageToSubProcess(
				businessKey, "receivetask1", Collections.singletonMap("b", 7));
		assertEquals(subProcessId, sendMessageId);
		assertEquals(7, runtimeService.getVariable(subProcessId, "b"));
		// checked ability to modify variable in the right process instance
		assertEquals(1, taskQuery2.count());
		assertEquals(0, receiveTaskQueryRight.count());
		// checked completion of receive task

	}

	// TODO :fix these unit tests for the current version of Activiti

	// @Test
	// @Deployment(resources = { "diagrams/event-based/HandleCancel.bpmn",
	// "diagrams/event-based/SubPro.bpmn" })
	// public void testSendSignalByBusinessKey() {
	// Map<String, Object> variables = new HashMap<String, Object>();
	// variables.put("a", 3);
	// variables.put("b", 8);
	// String businessKey = "BK" + new Random().nextLong();
	// ProcessInstance processInstance = runtimeService
	// .startProcessInstanceByKey("HandleCancel", businessKey,
	// variables);
	// String processInstanceId = processInstance.getId();
	// // process started
	// String subProcessId = historyService
	// .createHistoricProcessInstanceQuery()
	// .superProcessInstanceId(processInstanceId).singleResult()
	// .getId();
	// assertEquals("handle signal at top level not started yet", 0,
	// userTaskTopCount(processInstanceId));
	// assertEquals("sub process running", 1, userTaskSubCount(subProcessId));
	// new MessageSender(processEngine).sendSignal(businessKey,
	// "cancelSignalName", null);
	// assertEquals("handle at top level started", 1,
	// userTaskTopCount(processInstanceId));
	// assertEquals("subprocess deleted", 0, userTaskSubCount(subProcessId));
	// }
	//
	// @Test
	// @Deployment(resources = { "diagrams/event-based/HandleCancel.bpmn",
	// "diagrams/event-based/SubPro.bpmn" })
	// public void testSendSignalByProcessId() {
	// ProcessInstance processInstance = runtimeService
	// .startProcessInstanceByKey("HandleCancel");
	// String processInstanceId = processInstance.getId();
	// // process started
	// String subProcessId = historyService
	// .createHistoricProcessInstanceQuery()
	// .superProcessInstanceId(processInstanceId).singleResult()
	// .getId();
	// assertEquals("handle signal at top level not started yet", 0,
	// userTaskTopCount(processInstanceId));
	// assertEquals("sub process running", 1, userTaskSubCount(subProcessId));
	// new MessageSender(processEngine).sendSignalByProcessId(
	// "cancelSignalName", null, processInstanceId);
	// assertEquals("handle at top level started", 1,
	// userTaskTopCount(processInstanceId));
	// assertEquals("subprocess deleted", 0, userTaskSubCount(subProcessId));
	// }
	//
	// private long userTaskSubCount(String subProcessId) {
	// return taskService.createTaskQuery().processInstanceId(subProcessId)
	// .taskDefinitionKey("usertaskSub").count();
	// }
	//
	// private long userTaskTopCount(String processInstanceId) {
	// return taskService.createTaskQuery()
	// .processInstanceId(processInstanceId)
	// .taskDefinitionKey("usertaskTop").count();
	// }

	private ExecutionQuery receiveTaskQuery(String businessKey) {
		return runtimeService.createExecutionQuery()
				.processInstanceBusinessKey(businessKey)
				.activityId("receivetask1");
	}

	private ExecutionQuery receiveTaskQueryByInstanceId(String instanceId) {
		return runtimeService.createExecutionQuery()
				.processInstanceId(instanceId).activityId("receivetask1");
	}

}
