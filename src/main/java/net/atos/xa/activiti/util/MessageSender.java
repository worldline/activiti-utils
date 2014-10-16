package net.atos.xa.activiti.util;

import static java.lang.String.format;

import java.util.Map;
import java.util.Map.Entry;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;

public class MessageSender {
	private final RuntimeService runtimeService;
	private final ProcessEngine processEngine;

	public MessageSender(ProcessEngine processEngine) {
		this.processEngine = processEngine;
		runtimeService = processEngine.getRuntimeService();
	}

	public void sendMessageEventReceived(String messageRef, String businessKey,
			Map<String, Object> variablesToSet) {
		String exId = runtimeService.createExecutionQuery()
				.messageEventSubscriptionName(messageRef)
				.processInstanceBusinessKey(businessKey).singleResult().getId();
		runtimeService.messageEventReceived(messageRef, exId, variablesToSet);
	}

	public void sendMessageEventReceivedToSubProcess(String messageRef,
			String piId, Map<String, Object> variablesToSet) {
		Queries queries = new Queries(processEngine);
		for (String id : queries.recursiveSubprocesses(piId)) {
			Execution exec = runtimeService.createExecutionQuery()
					.messageEventSubscriptionName(messageRef)
					.processInstanceId(id).singleResult();
			if (exec != null) {
				String exId = exec.getId();
				runtimeService.messageEventReceived(messageRef, exId,
						variablesToSet);
			}
		}
	}

	/**
	 * Allows a process to continue when waiting on a receive task. This only
	 * works if the receiveTask is directly in the process (not under a call
	 * activity).
	 * 
	 * @param businessKey
	 * @param receiveTaskId
	 * @param variablesToSet
	 *            content of the message or null
	 * @return the process instance id or null if no match was found
	 */
	public String sendMessage(String businessKey, String receiveTaskId,
			Map<String, ?> variablesToSet) {
		ExecutionQuery query = runtimeService.createExecutionQuery()
				.processInstanceBusinessKey(businessKey);
		if (receiveTaskId != null) {
			query.activityId(receiveTaskId);
		}
		return setVariables(variablesToSet, query);
	}

	public String sendMessage(String variableName, String variableValue,
			String receiveTaskId, Map<String, ?> variablesToSet) {
		ExecutionQuery query = runtimeService.createExecutionQuery()
				.variableValueEquals(variableName, variableValue);
		if (receiveTaskId != null) {
			query.activityId(receiveTaskId);
		}
		return setVariables(variablesToSet, query);
	}

	/**
	 * Allows a subprocess to continue when waiting on a receive task. This is
	 * designed for the case when the receive task is under one or several
	 * nested call activity tasks.
	 * 
	 * @param businessKey
	 * @param receiveTaskId
	 * @param variablesToSet
	 *            content of the message or null
	 * @return the subprocess instance id or null if no match was found
	 */
	public String sendMessageToSubProcess(String businessKey,
			String receiveTaskId, Map<String, ?> variablesToSet) {
		for (String subprocessId : new Queries(processEngine)
				.recursiveSubprocessesByBusinessKey(businessKey)) {
			ExecutionQuery query = runtimeService.createExecutionQuery()
					.processInstanceId(subprocessId);
			if (receiveTaskId != null) {
				query.activityId(receiveTaskId);
			}
			if (query.count() == 1) {
				return setVariables(variablesToSet, query);
			}
		}
		return null;
	}

	public String sendMessageToSubProcess(String variableName,
			String variableValue, String receiveTaskId,
			Map<String, ?> variablesToSet) {
		for (String subprocessId : new Queries(processEngine)
				.recursiveSubProcessesByVariable(variableName, variableValue)) {
			ExecutionQuery query = runtimeService.createExecutionQuery()
					.processInstanceId(subprocessId);
			if (receiveTaskId != null) {
				query.activityId(receiveTaskId);
			}
			if (query.count() == 1) {
				return setVariables(variablesToSet, query);
			}
		}
		return null;
	}

	private String setVariables(Map<String, ?> variablesToSet,
			ExecutionQuery query) {
		Execution receiveTask = query.singleResult();
		if (receiveTask != null) {
			if (variablesToSet != null) {
				runtimeService.setVariables(receiveTask.getProcessInstanceId(),
						variablesToSet);
			}
			runtimeService.signal(receiveTask.getId());
			return receiveTask.getProcessInstanceId();
		} else {
			return null;
		}
	}

	/**
	 * Sends a signal to a specific process instance.
	 * 
	 * @param businessKey
	 *            to identify the process instance
	 * @param signalName
	 *            as defined as the "name" property of the signal at the BP
	 *            level (_not_ at the boundary signal level)
	 * @param variablesToSet
	 *            the payload (may be null)
	 * @return the process instance id which was signaled
	 * @throws ActivitiException
	 *             if no subscription is found for this signal and this process
	 *             instance
	 */
	public String sendSignal(String businessKey, String signalName,
			Map<String, Object> variablesToSet) {
		String processId = runtimeService.createExecutionQuery()
				.processInstanceBusinessKey(businessKey).singleResult().getId();
		if (1 <= sendSignalByProcessId(signalName, variablesToSet, processId)) {
			return processId;
		} else {
			throw new ActivitiException(
					format("no subscription found for signal '%s' and businessKey '%s'",
							signalName, businessKey));
		}
	}

	public String sendSignal(String variableName, String variableValue,
			String signalName, Map<String, Object> variablesToSet) {
		String processId = runtimeService.createExecutionQuery()
				.variableValueEquals(variableName, variableValue)
				.singleResult().getId();
		if (1 <= sendSignalByProcessId(signalName, variablesToSet, processId)) {
			return processId;
		} else {
			throw new ActivitiException(
					format("no subscription found for signal '%s' and variable '%s' equals to '%s'",
							signalName, variableName, variableValue));
		}

	}

	/**
	 * @return the number of processes signaled (normally one)
	 */
	public int sendSignalByProcessId(String signalName,
			Map<String, Object> variablesToSet, String processId) {
		int found = 0;
		for (Execution execution : runtimeService.createExecutionQuery()
				.signalEventSubscriptionName(signalName).list()) {
			if (processId.equals(execution.getProcessInstanceId())) {
				runtimeService.signalEventReceived(signalName,
						execution.getId(), variablesToSet);
				found++;
			}
		}
		return found;
	}

	/**
	 * Sends a message to any subprocess of the parent process identified with
	 * the businessKey, if the subprocess variables match the criteria.
	 * 
	 * @param businessKey
	 *            to select the parent process
	 * @param criteria
	 *            to select the subprocesses
	 */
	public void sendMessage(String businessKey, String activityId,
			Map<String, ?> variablesToSet, Map<String, ?> criteria) {
		for (String subProcessId : new Queries(processEngine)
				.recursiveSubprocessesByBusinessKey(businessKey)) {
			ExecutionQuery query = runtimeService.createExecutionQuery()
					.processInstanceId(subProcessId).activityId(activityId);
			for (Entry<String, ?> entry : criteria.entrySet()) {
				query.variableValueEquals(entry.getKey(), entry.getValue());
			}
			for (Execution execution : query.list()) {
				runtimeService.signal(execution.getId());
				if (variablesToSet != null)
					runtimeService.setVariables(
							execution.getProcessInstanceId(), variablesToSet);
			}
		}
	}

	public void sendMessage(String variableName, String variableValue,
			String activityId, Map<String, ?> variablesToSet,
			Map<String, ?> criteria) {
		for (String subProcessId : new Queries(processEngine)
				.recursiveSubProcessesByVariable(variableName, variableValue)) {
			ExecutionQuery query = runtimeService.createExecutionQuery()
					.processInstanceId(subProcessId).activityId(activityId);
			for (Entry<String, ?> entry : criteria.entrySet()) {
				query.variableValueEquals(entry.getKey(), entry.getValue());
			}
			for (Execution execution : query.list()) {
				runtimeService.signal(execution.getId());
				if (variablesToSet != null)
					runtimeService.setVariables(
							execution.getProcessInstanceId(), variablesToSet);
			}
		}
	}

}
