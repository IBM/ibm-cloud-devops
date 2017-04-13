package draplugin.notification.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import javax.inject.Inject;
import draplugin.notification.MessageHandler;
import draplugin.dra.Util;
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
        String stageName = step.getStageName().trim();
        String status = step.getStatus().trim();
        PrintStream printStream = listener.getLogger();
        String webhookUrl;

        if(Util.isNullOrEmpty(step.getWebhookUrl())){
            webhookUrl = envVars.get("IBM_CLOUD_DEVOPS_WEBHOOK_URL");

            //backward compatability
            if(Util.isNullOrEmpty(webhookUrl)){
                webhookUrl = envVars.get("ICD_WEBHOOK_URL");
            }
        } else {
            webhookUrl = step.getWebhookUrl();
        }

        //check all the required env vars
        if (!Util.allNotNullOrEmpty(stageName, status)) {
            printStream.println("[IBM Cloud DevOps] Required parameter null or empty.");
            printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
            return null;
        }

        JSONObject message = MessageHandler.buildMessage(build, envVars, stageName, status);
        MessageHandler.postToWebhook(webhookUrl, message, printStream);

        return null;
    }
}
