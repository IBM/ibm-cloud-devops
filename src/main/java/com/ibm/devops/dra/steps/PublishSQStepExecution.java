/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra.steps;

import com.ibm.devops.dra.PublishSQ;
import com.ibm.devops.dra.Util;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.PrintStream;
import java.util.HashMap;

import static com.ibm.devops.dra.AbstractDevOpsAction.setRequiredEnvVars;

public class PublishSQStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
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

        HashMap<String, String> requiredEnvVars = setRequiredEnvVars(step, envVars);

        //check all the required env vars
        if (!Util.allNotNullOrEmpty(requiredEnvVars, printStream)) {
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Test Result.");
            return null;
        }

        //check all the required parameters
        HashMap<String, String> requiredParams = new HashMap<>();
        requiredParams.put("SQProjectKey", step.getSQProjectKey());
        requiredParams.put("SQHostURL", step.getSQHostURL());
        requiredParams.put("SQAuthToken", step.getSQAuthToken());

        if (!Util.allNotNullOrEmpty(requiredParams, printStream)) {
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload SonarQube Test Result.");
            return null;
        }

        // optional build number, if user wants to set their own build number
        String buildNumber = step.getBuildNumber();

        PublishSQ publisher = new PublishSQ(requiredEnvVars, requiredParams);

        if (!Util.isNullOrEmpty(buildNumber)) {
            publisher.setBuildNumber(buildNumber);
        }
        publisher.perform(build, ws, launcher, listener);

        return null;
    }
}
