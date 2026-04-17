package io.flowset.demo.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SayHelloDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(SayHelloDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("JavaDelegate: Hello, World!");
    }
}