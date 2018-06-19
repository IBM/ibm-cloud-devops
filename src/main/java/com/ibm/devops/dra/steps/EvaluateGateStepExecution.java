/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

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
import java.util.HashMap;

import static com.ibm.devops.dra.AbstractDevOpsAction.setRequiredEnvVars;
import static com.ibm.devops.dra.UIMessages.*;
import static com.ibm.devops.dra.Util.allNotNullOrEmpty;
import static com.ibm.devops.dra.Util.isNullOrEmpty;

public class EvaluateGateStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
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
        HashMap<String, String> requiredEnvVars = setRequiredEnvVars(step, envVars);

        //check all the required env vars
        if (!allNotNullOrEmpty(requiredEnvVars, printStream)) {
            printStream.println(getMessageWithPrefix(MISS_REQUIRED_ENV_VAR));
            return null;
        }

        String policy = step.getPolicy();
        if (isNullOrEmpty(policy)) {
            printStream.println(getMessageWithVar(MISS_REQUIRED_STEP_PARAMS, "evaluateGate", "policy"));
            return null;
        }

        Boolean willDisrupt = false;
        if (!isNullOrEmpty(step.getForceDecision()) && step.getForceDecision().toLowerCase().equals("true")) {
            willDisrupt = true;
        }

        // optional build number, if user wants to set their own build number
        String buildNumber = step.getBuildNumber();
        EvaluateGate evaluateGate = new EvaluateGate(requiredEnvVars, policy, step.getEnvironment(), willDisrupt);
        try {
            if (!isNullOrEmpty(buildNumber)) {
                evaluateGate.setBuildNumber(buildNumber);
            }
            evaluateGate.perform(build, ws, launcher, listener);
        } catch (AbortException e) {
            throw new AbortException("Decision is fail");
        }

        return null;
    }


}
