package com.ibm.devops.dra.steps;

import com.ibm.devops.dra.EvaluateGate;
import com.ibm.devops.dra.Util;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.PrintStream;

/**
 * Created by lix on 3/23/17.
 */
public class EvaluateGateStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient EvaluateGateStep step;

    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient FilePath ws;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter
    private transient Run build;
    @StepContextParameter
    private transient EnvVars envVars;

    @Override
    protected Void run() throws Exception {

        PrintStream printStream = listener.getLogger();

        String orgName = Util.isNullOrEmpty(step.getOrgName()) ? envVars.get("IBM_CLOUD_DEVOPS_ORG") : step.getOrgName();
        String applicationName =  Util.isNullOrEmpty(step.getApplicationName()) ? envVars.get("IBM_CLOUD_DEVOPS_APP_NAME") : step.getApplicationName();
        String toolchainName = Util.isNullOrEmpty(step.getToolchainId()) ? envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID") : step.getToolchainId();
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");

        //check all the required env vars
        if (!Util.allNotNullOrEmpty(orgName, applicationName,toolchainName, username, password)) {
            printStream.println("[IBM Cloud DevOps] Missing environment variables configurations, please specify all required environment variables in the pipeline");
            printStream.println("[IBM Cloud DevOps] Error: Failed to get Gate decision.");
            return null;
        }

        String policy = step.getPolicy();
        if (Util.isNullOrEmpty(policy)) {
            printStream.println("[IBM Cloud DevOps] evaluateGate is missing required parameters, " +
                    "please make sure you specify \"policy\"");
            printStream.println("[IBM Cloud DevOps] Error: Failed to run evaluate Gate.");
            return null;
        }

        Boolean willDisrupt = false;
        if (!Util.isNullOrEmpty(step.getForceDecision()) && step.getForceDecision().toLowerCase().equals("true")) {
            willDisrupt = true;
        }

        EvaluateGate evaluateGate = new EvaluateGate(
                policy,
                orgName,
                applicationName,
                toolchainName,
                step.getEnvironment(),
                username,
                password,
                willDisrupt);
        try {
            evaluateGate.perform(build, ws, launcher, listener);
        } catch (AbortException e) {
            throw new AbortException("Decision is fail");
        }

        return null;
    }
}
