package net.atos.xa.activiti.util;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;

public class FailingServiceTask implements JavaDelegate {
	static int count = 0;

	@Override
	public void execute(DelegateExecution execution) throws Exception {
		count++;
		System.out.println("failing, time :" + count);
		throw new Exception("failing");
	}

}
