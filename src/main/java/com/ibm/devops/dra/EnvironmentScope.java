/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class EnvironmentScope extends AbstractDescribableImpl<EnvironmentScope> {
    private boolean isBuild;
    private boolean isAll;
    private boolean isDeploy;
    private String branchName;
    private String envName;

    @DataBoundConstructor
    public EnvironmentScope(String value, String branchName, String envName) {
        switch (value) {
            case "build":
                this.isBuild = true;
                this.isDeploy = false;
                this.isAll = false;
                break;
            case "deploy":
                this.isDeploy = true;
                this.isBuild = false;
                this.isAll = false;
                break;
            default:
                this.isAll = true;
                this.isBuild = false;
                this.isDeploy = false;
                break;
        }

        this.branchName = branchName;
        this.envName = envName;
    }

    public boolean isBuild() {
        return isBuild;
    }

    public boolean isAll() {
        return isAll;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<EnvironmentScope> {
        @Override
        public String getDisplayName() {
            return "DevOps Insight Test Environment Scope";
        }
    }
}
