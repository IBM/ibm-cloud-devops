# DRA-Jenkins

This plug-in provide customized build steps and status to use DevOps Insights in Jenkins projects.

## Usage of this plugin

### Set up Jenkins environment

To use this plugin, user has to have Jenkins local environment or the access to the Jenkins server.

### Set up IBM Bluemix Toolchain with DevOps Insights

Click [here](https://console.stage1.ng.bluemix.net/devops/create) to create a toolchain and add **DevOps Insights** service to it.

### Installing the plugins

  Install the DevOps Insights plugin in your Jenkins project:

  1. [Download the IBM DevOps Insight Plugin installation file (.hpi)](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/release/target/dra.hpi) from the plugin's GitHub repository.
  2. In your Jenkins installation, click **Manage Jenkins**, select **Manage Plugins**, and select the **Advanced** tab.
  3. Click **Choose File** and select the DevOps Insight plugin installation file.
  4. Click **Upload**.
  5. Restart Jenkins and verify that the plugin was installed successfully.

### Integrating DevOps Insights with your Jenkins project

When the plugin is installed, you can integrate DevOps Insights into your Jenkins project. DevOps Insights provide you with two main features, get a project dashboard by uploading the test result to DevOps Insight and have a quality gate before deploying your project **<need more text to explain about toolchains and it's benefits????>**
You can go to the [control center](https://control-center.ng.bluemix.net/) to view the dashboard or create a policy for the quality gate that you are going to use for your project

1. Open the configuration of any jobs (build job, test jobs, deployment job, etc)
 (unit test, code coverage, functional verification test or e2e test, etc) that you already have, 
2. Add a post-build action with the corresponding type (**Publish build information to DevOps Insights**, **Publish test result to DevOps Insights** and **Publish deployment information to DevOps Insights**)  to your job. Complete the required fields. 
 - For the Credential field, choose your Bluemix ID and password from the dropdown if you already store them in the Jenkins. If not, click Add button to create a new one. You can use the **Test Connection** button to test your connection to the Bluemix
 - For the Build Job Name, specify your Build job name in Jenkins. If you have the build and test in this jenkins job together, leave this field empty. If you have the build job outside of Jenkins, you can check **if builds are being done outside of Jenkins	Help for feature: if builds are being done outside of Jenkins
** checkbox and specify the build number and url
 - For the environment, if the tests are running in build stage, just select the *Build Environment*; if the tests are running in deployment stage, select the *Deploy Environment* and you can specify the environment name in it. Currently two values are supported: **STAGING** and **PRODUCTION**
 - For the Result file location, specify your result file location. If you don't have any result file generated, leave this field empty and the plugin will upload a default result file based on the status of current test job
 
     Here are some example configurations:
     ![Upload Build Information](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/master/screenshots/Upload-Build-Info.png "Publish Build Information to DRA")
     ![Upload Test Result](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/master/screenshots/Upload-Test-Result.png "Publish Test Result to DRA")
     ![Upload Deployment Information](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/master/screenshots/Upload-Deployment-Info.png "Publish Deployment Information to DRA")

3. (Optional) If you want to have DevOps Insights policy gates to control the downstream deploy job, you can choose the "Evaluate Gate Policy" option in the **Publish test result to DevOps Insights** and choose the policy or add another post-build action with the type **DevOps Insights Gate** and complete the required fields. You can specify the scope of test result for Build, Deploy or all environment. The gate will not stop triggering the downstream job if the test job fails to meet the policy that you define in the Control Center unless you check the "*Fail the build based on the policy rules*"
    
    Here is an example gate configuration:
    ![DevOps Insights Gate](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/master/screenshots/DRA-Gate.png "DevOps Insights Gate")

4. Click **Apply** and then **Save**.
5. To run the project, click **Build Now** from the project page
6. After the build runs, you can go to the [control center](https://control-center.ng.bluemix.net/) to check your build status in the dashboard and  if you have gate set up, you can also see DevOps Insights results on the Status page of current build.

## License

Copyright (c)2016 IBM Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
