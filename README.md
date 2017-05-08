# IBM Cloud DevOps

With this Jenkins plugin, You can publish test results to DevOps Insights, add automated quality gates, and track your deployment risk.  You can also send your Jenkins job notifications to other tools in your toolchain, such as Slack and PagerDuty. To help you figure out when code was deployed, the system can add deployment messages to your Git commits and your related Git or JIRA issues. You can also view your deployments on the Toolchain Connections page.

This plugin provides Post-Build Actions and CLIs to support this inteigration. DevOps Insights aggregates and analyzes the results from unit tests, functional tests, code-coverage tools, static security code scans and dynamic security code scans to determine whether your code meets predefined policies at gates in your deployment process. If your code does not meet or exceed a policy, the deployment is halted, preventing risky changes from being released. You can use DevOps Insights as a safety net for your continuous delivery environment, a way to implement and improve quality standards over time, and a data visualization tool to help you understand your project's health.

## Prerequisites

You must have access to a server that is running a Jenkins project.

## 1. Create a toolchain

Before you can integrate DevOps Insights with a Jenkins project, you must create a toolchain. A *toolchain* is a set of tool integrations that support development, deployment, and operations tasks. The collective power of a toolchain is greater than the sum of its individual tool integrations. Toolchains are part of the IBM Bluemix&reg; Continuous Delivery service. To learn more about the Continuous Delivery service, see [its documentation](https://console.ng.bluemix.net/docs/services/ContinuousDelivery/cd_about.html).

1. To create a toolchain, go to the [Create a Toolchain page](https://console.ng.bluemix.net/devops/create) and follow the instructions on that page.

2. After you create the toolchain, add DevOps Insights to it. For instructions, see the [DevOps Insights documentation](https://console.ng.bluemix.net/docs/services/DevOpsInsights/index.html).

## 2. (optional) Configure Jenkins jobs for Deployment Risk dashboard

If you would like to make use Deployment Risk dashboard, follow these steps.

After the plugin is installed, you can integrate DevOps Insights into your Jenkins project.


### General workflow

1. Open the configuration of any jobs that you have, such as build, test, or deployment.

2. Add a post-build action for the corresponding type:

   * For build jobs, use **Publish build information to IBM Cloud DevOps**.

   * For test jobs, use **Publish test result to IBM Cloud DevOps**.

   * For deployment jobs, use **Publish deployment information to IBM Cloud DevOps**.

3. Complete the required fields:

   * From the **Credentials**, select your Bluemix ID and password. If they are not saved in Jenkins, click **Add** to add and save them. Click **Test Connection** to test your connection with Bluemix.

   * In the **Build Job Name** field, specify your build job's name exactly as it is in Jenkins. If the build occurs with the test job, leave this field empty. If the build job occurs outside of Jenkins, select the **Builds are being done outside of Jenkins** check box and specify the build number and build URL.

   * For the environment, if the tests are running in build stage, select only the build environment. If the tests are running in the deployment stage, select the deploy environment and specify the environment name. Two values are supported: `STAGING` and `PRODUCTION`.

   * For the **Result File Location** field, specify the result file's location. If the test doesn't generate a result file, leave this field empty. The plugin uploads a default result file that is based on the status of current test job.

   **Example configurations**

   ![Upload Build Information](https://github.com/IBM/ibm-cloud-devops/blob/master/screenshots/Upload-Build-Info.png "Publish Build Information to DRA")

   ![Upload Test Result](https://github.com/IBM/ibm-cloud-devops/blob/master/screenshots/Upload-Test-Result.png "Publish Test Result to DRA")

   ![Upload Deployment Information](https://github.com/IBM/ibm-cloud-devops/blob/master/screenshots/Upload-Deployment-Info.png "Publish Deployment Information to DRA")

4. (Optional): If you want to use DevOps Insights policy gates to control a downstream deploy job, add a post build action, **IBM Cloud DevOps Gate**. Choose a policy and specify the scope of the test results. To allow the policy gates to prevent downstream deployments, select the **Fail the build based on the policy rules** check box. The following image shows an example configuration:

    ![DevOps Insights Gate](https://github.com/IBM/ibm-cloud-devops/blob/master/screenshots/DRA-Gate.png "DevOps Insights Gate")

5. Run your Jenkins Build job.

6. Go to the [IBM Bluemix DevOps](https://console.ng.bluemix.net/devops), select your toolchain and click on DevOps Insights card to view Deployment Risk dashboard.


## 3. (Optional) Configure Jenkins jobs to send notifications to tools in your toolchain (e.g., Slack, PagerDuty)

Configure your Jenkins jobs to send notifications to your toolchain by following the instructions in the [Bluemix Docs](https://console.ng.bluemix.net/docs/services/ContinuousDelivery/toolchains_integrations.html#jenkins).


   **Example configurations**
  * Configuring the IBM_CLOUD_DEVOPS_WEBHOOK_URL for job configurations: ![Set IBM_CLOUD_DEVOPS_WEBHOOK_URL Parameter](https://github.com/IBM/ibm-cloud-devops/blob/master/screenshots/Set-Parameterized-Webhook.png "Set Parameterized WebHook")
  * Configuring post-build actions for job notifications: ![Post-build Actions for WebHook notification](https://github.com/IBM/ibm-cloud-devops/blob/master/screenshots/PostBuild-WebHookNotification.png "Configure WebHook Notification in Post-build Actions")


## License

Copyright&copy; 2016, 2017 IBM Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
