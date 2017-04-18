package draplugin.notification;

import draplugin.dra.Util;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Created by patrickjoy on 4/18/17.
 */
public final class EventHandler {
    public static OTCNotifier findPublisher(AbstractBuild r){
        List<Publisher> publisherList = r.getProject().getPublishersList().toList();

        //ensure that there is an OTCNotifier in the project
        for(Publisher publisher: publisherList){
            if(publisher instanceof OTCNotifier){
                return (OTCNotifier) publisher;
            }
        }

        return null;
    }

    public static EnvVars getEnv(AbstractBuild r, TaskListener listener, PrintStream printStream){
        try {
            return r.getEnvironment(listener);
        } catch (IOException e) {
            printStream.println("[IBM Cloud DevOps] Exception: ");
            printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
            e.printStackTrace(printStream);
        } catch (InterruptedException e) {
            printStream.println("[IBM Cloud DevOps] Exception: ");
            printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
            e.printStackTrace(printStream);
        }

        return null;
    }

    public static boolean isRelevant(OTCNotifier notifier, String phase, Result result){
        boolean onStarted;
        boolean onCompleted;
        boolean onFinalized;
        boolean failureOnly;

        //Make sure OTC Notifier was found in the publisherList
        if(notifier != null){
            onStarted = notifier.getOnStarted();
            onCompleted = notifier.getOnCompleted();
            onFinalized = notifier.getOnFinalized();
            failureOnly = notifier.getFailureOnly();

            if(onStarted && phase == "STARTED" || onCompleted && phase == "COMPLETED" || onFinalized && phase == "FINALIZED"){//check selections
                if(failureOnly && result == Result.FAILURE || !failureOnly){//check failureOnly
                    return true;
                }
            }
        }

        return false;
    }

    public static String getWebhookFromEnv(EnvVars envVars){
        String webhook = null;

        if(envVars != null) {
            webhook = envVars.get("IBM_CLOUD_DEVOPS_WEBHOOK_URL");

            //backward compatibility
            if (Util.isNullOrEmpty(webhook)) {
                webhook = envVars.get("ICD_WEBHOOK_URL");
            }
        }

        return webhook;
    }
}
