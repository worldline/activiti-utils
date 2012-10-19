package net.atos.xa.activiti.util;

import java.util.ArrayList;
import java.util.List;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.ProcessInstance;

public class Queries {
	private final HistoryService historyService;
	private final RuntimeService runtimeService;

	public Queries(ProcessEngine processEngine) {
		historyService = processEngine.getHistoryService();
		runtimeService = processEngine.getRuntimeService();
	}

	public List<String> recursiveSubprocessesByBusinessKey(
			String businessKey) {
		String processInstanceId = runtimeService.createProcessInstanceQuery()
				.processInstanceBusinessKey(businessKey).singleResult()
				.getProcessInstanceId();
		return recursiveSubprocesses(processInstanceId);
	}
	
	public List<String> recursiveSubProcessesByVariable(String variableName, String variableValue){
		//String processInstanceId = 
		List<ProcessInstance> piList=		runtimeService.createProcessInstanceQuery()
				.variableValueEquals(variableName,variableValue).list();
				//.getProcessInstanceId();
		return recursiveSubprocesses(piList.get(0).getProcessInstanceId()
				//processInstanceId
				);
	}

	public List<String> recursiveSubprocesses(String parentProcessId) {
		List<String> subprocesses = new ArrayList<String>();
		for (HistoricProcessInstance historicProcessInstance : historyService
				.createHistoricProcessInstanceQuery()
				.superProcessInstanceId(parentProcessId).list()) {
			String subProcessId = historicProcessInstance.getId();
			subprocesses.add(subProcessId);
			subprocesses.addAll(recursiveSubprocesses(subProcessId));
		}
		return subprocesses;
	}
}
