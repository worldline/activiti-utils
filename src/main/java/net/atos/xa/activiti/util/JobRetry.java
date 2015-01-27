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

	public JobRetry(ProcessEngine processEngine) {
		this.processEngine = processEngine;
		managementService = processEngine.getManagementService();
		processEngineConfiguration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
	}

	public void setRetries(final String processInstanceId, final int retries) {
		final Job job = managementService.createJobQuery().processInstanceId(processInstanceId).singleResult();
		CommandExecutor commandExecutor = processEngineConfiguration.getCommandExecutor();
		commandExecutor.execute(new Command<Void>() {

			public Void execute(CommandContext commandContext) {
				JobEntity timer = commandContext.getDbSqlSession().selectById(JobEntity.class, job.getId());
				timer.setRetries(retries);
				return null;
			}

		});
	}
}
