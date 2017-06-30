/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra.steps;

import com.ibm.devops.dra.PublishBuild;
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

public class PublishBuildStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
    @Inject
    private transient PublishBuildStep step;

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

        // optional build number, if user wants to set their own build number
        String buildNumber = envVars.get("IBM_CLOUD_DEVOPS_BUILD_NUMBER");

        //check all the required env vars
        if (!Util.allNotNullOrEmpty(orgName, applicationName,toolchainName, username, password)) {
            printStream.println("[IBM Cloud DevOps] Missing environment variables configurations, please specify all required environment variables in the pipeline");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Record.");
            return null;
        }

        //check all the required parameters
        String result = step.getResult();
        String gitRepo = step.getGitRepo();
        String gitBranch = step.getGitBranch();
        String gitCommit = step.getGitCommit();
        if (!Util.allNotNullOrEmpty(result, gitRepo, gitBranch, gitCommit)) {
            printStream.println("[IBM Cloud DevOps] publishBuildRecord is missing required parameters, " +
                    "please make sure you specify \"result\", \"gitRepo\", \"gitBranch\", \"gitCommit\"");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Record.");
            return null;
        }

        if (result.equals("SUCCESS") || result.equals("FAIL")) {
            PublishBuild publishBuild = new PublishBuild(
                    result,
                    gitRepo,
                    gitBranch,
                    gitCommit,
                    orgName,
                    applicationName,
                    toolchainName,
                    username,
                    password);

            if (!Util.isNullOrEmpty(buildNumber)) {
                publishBuild.setBuildNumber(buildNumber);
            }
            publishBuild.perform(build, ws, launcher, listener);
        } else {
            printStream.println("[IBM Cloud DevOps] the \"result\" in the publishBuildRecord should be either \"SUCCESS\" or \"FAIL\"");
        }

        return null;
    }
}
