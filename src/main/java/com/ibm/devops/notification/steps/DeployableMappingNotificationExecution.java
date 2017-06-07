/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification.steps;

import com.ibm.devops.dra.Util;
import com.ibm.devops.notification.MessageHandler;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import javax.inject.Inject;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import net.sf.json.JSONObject;


import java.io.PrintStream;

public class DeployableMappingNotificationExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
    @Inject
    private transient DeployableMappingNotificationStep step;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Run build;
    @StepContextParameter
    private transient EnvVars envVars;

    @Override
    protected Void run() throws Exception {
        PrintStream printStream = listener.getLogger();
        String webhookUrl;

        if(Util.isNullOrEmpty(step.getWebhookUrl())){
            webhookUrl = Util.getWebhookUrl(envVars);
        } else {
            webhookUrl = step.getWebhookUrl();
        }

        String status = step.getStatus().trim();
        //check all the required env vars
        if (!Util.allNotNullOrEmpty(status)) {
            printStream.println("[IBM Cloud DevOps] Required parameter null or empty.");
            printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
            return null;
        }
        
        if ("SUCCESS".equals(status)) { // send deployable mapping message on for successful builds
        	JSONObject message = MessageHandler.buildDeployableMappingMessage(envVars, printStream);
        	MessageHandler.postToWebhook(webhookUrl, true, message, printStream);
        }
        return null;
    }
}
