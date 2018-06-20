/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

public class PublishBuildStep extends AbstractDevOpsStep {
    // required parameters to support pipeline script
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;

    // custom build number, optional
    private String buildNumber;

    @DataBoundConstructor
    public PublishBuildStep(String result, String gitRepo, String gitBranch, String gitCommit) {
        this.gitRepo = gitRepo;
        this.gitBranch = gitBranch;
        this.gitCommit = gitCommit;
        this.result = result;
    }

    @DataBoundSetter
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getGitRepo() {
        return gitRepo;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getResult() {
        return result;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(PublishBuildStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "publishBuildRecord";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish build record to IBM Cloud DevOps";
        }
    }
}
