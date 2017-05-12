/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification;

import com.ibm.devops.dra.Util;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

public final class EventHandler {
    /*
        find OTCNotifer in the publisher list
     */
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

    /*
        return the build env variables
     */
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

    /*
        Check job config to see if message should be sent.
     */
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

            if(onStarted && "STARTED".equals(phase) || onCompleted && "COMPLETED".equals(phase)
                    || onFinalized && "FINALIZED".equals(phase)){//check selections
                if(failureOnly && result != null && result.equals(Result.FAILURE) || !failureOnly){//check failureOnly
                    return true;
                }
            }
        }

        return false;
    }

    /*
        get the webhook from the build env
     */
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
