package net.atos.xa.activiti.util;

import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.persistence.entity.JobEntity;
import org.activiti.engine.runtime.Job;

public class JobRetry {
	private final ProcessEngine processEngine;
	private final ManagementService managementService;
	private final ProcessEngineConfigurationImpl processEngineConfiguration;
	private final Queries queries;

	public JobRetry(ProcessEngine processEngine) {
		this.processEngine = processEngine;
		queries = new Queries(processEngine);
		managementService = processEngine.getManagementService();
		processEngineConfiguration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
	}

	public void setRetries(final String processInstanceId, final int retries) {
		final Job job = findJob(processInstanceId);
		if (job != null) {
			setRetriesImpl(job.getId(), retries);
		} else {
			throw new IllegalArgumentException("No job found for processInstance=" + processInstanceId);
		}
	}

	private Job findJob(final String processInstanceId) {
		return managementService.createJobQuery().processInstanceId(processInstanceId).singleResult();
	}

	public void setRetriesToSubprocesses(final String processInstanceId, final int retries) {
		boolean found = false;
		for (String subProcessId : queries.recursiveSubprocesses(processInstanceId)) {
			Job job = findJob(subProcessId);
			if (job != null) {
				setRetriesImpl(job.getId(), retries);
				found = true;
			}
		}
		if (!found) {
			throw new IllegalArgumentException("No job found for processInstance=" + processInstanceId);
		}
	}

	private void setRetriesImpl(final String jobId, final int retries) {
		CommandExecutor commandExecutor = processEngineConfiguration.getCommandExecutor();
		commandExecutor.execute(new Command<Void>() {

			public Void execute(CommandContext commandContext) {
				JobEntity timer = commandContext.getDbSqlSession().selectById(JobEntity.class, jobId);
				timer.setRetries(retries);
				return null;
			}

		});
	}
}
