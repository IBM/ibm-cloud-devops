package draplugin.notification.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import javax.inject.Inject;
import draplugin.notification.MessageHandler;
import draplugin.notification.Util;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import net.sf.json.JSONObject;


import java.io.PrintStream;

/**
 * Created by patrickjoy on 3/30/17.
 */
public class OTCNotificationExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient OTCNotificationStep step;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Run build;
    @StepContextParameter
    private transient EnvVars envVars;

    @Override
    protected Void run() throws Exception {
        String webhookUrl = Util.isNullOrEmpty(step.getWebhookUrl()) ? envVars.get("IBM_CLOUD_DEVOPS_WEBHOOK_URL") : step.getWebhookUrl();
        String stageName = step.getStageName().trim();
        String status = step.getStatus().trim();
        PrintStream printStream = listener.getLogger();

        //check webhookUrl
        if(Util.isNullOrEmpty(webhookUrl)) {
            printStream.println("[IBM Cloud DevOps] IBM_CLOUD_DEVOPS_WEBHOOK_URL not set.");
        } else {
            JSONObject message = MessageHandler.buildMessage(build, envVars, stageName, status);
            MessageHandler.postToWebhook(webhookUrl, message, printStream);
        }

        return null;
    }
}
