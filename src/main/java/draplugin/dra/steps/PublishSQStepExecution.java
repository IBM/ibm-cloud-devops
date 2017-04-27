package draplugin.dra.steps;

import draplugin.dra.PublishSQ;
import draplugin.dra.Util;
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
 * Created by charlie cox on 4/19/17.
 */
public class PublishSQStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient PublishSQStep step;

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
        String IBMusername = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String IBMpassword = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");
        // Project Key defaults to app name if nothing is passed in
        String SQProjectKey = step.getSQProjectKey();
        String SQHostURL = step.getSQHostURL();
        String SQAuthToken = step.getSQAuthToken();

        //check all the required env vars
        if (!Util.allNotNullOrEmpty(orgName, applicationName, toolchainName, IBMusername, IBMpassword, SQAuthToken, SQProjectKey, SQHostURL)) {
            printStream.println("[IBM Cloud DevOps] Missing environment variables configurations, please specify all required environment variables in the pipeline");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Test Result.");
            return null;
        }

        PublishSQ publisher = new PublishSQ(
                orgName,
                applicationName,
                toolchainName,
                SQProjectKey,
                SQHostURL,
                SQAuthToken,
                IBMusername,
                IBMpassword);
        publisher.perform(build, ws, launcher, listener);

        return null;
    }
}
