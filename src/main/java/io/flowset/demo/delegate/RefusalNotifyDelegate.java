package io.flowset.demo.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RefusalNotifyDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(RefusalNotifyDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Application rejected for process {}", execution.getProcessInstanceId());
    }
}