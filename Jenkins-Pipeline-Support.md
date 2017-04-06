To use IBM Cloud DevOps with the Jenkins pipeline project, you can follow the [declarative Jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Declarative-Jenkinsfile) or [scripted jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Scripted-Jenkinsfile)

## Prerequisites
Make sure you are using Jenkins 2.X and have all pipeline related plugins installed and updated to the latest version
It has been test for Jenkins pipeline job with Pipeline plugin 2.5 version.

## 1. Expose the required environment variables to all steps
The plugin requires 5 environment variables defined as follow:

1. `IBM_CLOUD_DEVOPS_CREDS` - the bluemix credentials ID that you defined in the jenkins, e.g `IBM_CLOUD_DEVOPS_CREDS = credentials('BM_CRED')`, by using the `credentials` command, it will set two environment variables automatically:
    1. `IBM_CLOUD_DEVOPS_CREDS_USR` for username
    2. `IBM_CLOUD_DEVOPS_CREDS_PSW` for password
2. `IBM_CLOUD_DEVOPS_ORG` - the bluemix org that you are going to use
3. `IBM_CLOUD_DEVOPS_APP_NAME` - the name of your application
4. `IBM_CLOUD_DEVOPS_TOOLCHAIN_ID` - the toolchain id that you are using, you can get the toolchain id from the url after the toolchain is created. e.g https://console.ng.bluemix.net/devops/toolchains/TOOLCHAIN_ID.
5. `IBM_CLOUD_DEVOPS_WEBHOOK_URL` - the webhook obtained from the Jenkins card on your toolchain.

In the plugin, we are going to refer to these environment variables and credentials to interact with IBM Cloud DevOps
Here is an example to use it in the Jenkinsfile (a.k.a Declarative Pipeline)

```
environment {
        IBM_CLOUD_DEVOPS_CREDS = credentials('BM_CRED')
        IBM_CLOUD_DEVOPS_ORG = 'dlatest'
        IBM_CLOUD_DEVOPS_APP_NAME = 'Weather-App'
        IBM_CLOUD_DEVOPS_TOOLCHAIN_ID = '1320cec1-daaa-4b63-bf06-7001364865d2'
        IBM_CLOUD_DEVOPS_WEBHOOK_URL = 'https://jenkins:5a55555a-a555-5555-5555-a555aa55a555:55555555-5555-5555-5555-555555555555@devops-api.ng.bluemix.net/v1/toolint/messaging/webhook/publish'
    }
```

Notes: `credentials` is only available for Declarative Pipeline. For those using Scripted Pipeline, see the documentation for the `withCredentials` step.
For the Scripted Pipeline, use `withEnv` instead of `environment`. You can refer to [Scripted Jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Scripted-Jenkinsfile) as an example


## 2. Use the IBM Cloud DevOps steps
We provide 4 steps to upload the build/test/deploy information and use the IBM Cloud DevOps Gate

### 1. publishBuildRecord
Publish the build record to the IBM Cloud DevOps, there are 4 required parameters:

1. gitBranch
2. gitCommit
3. gitRepo
4. result - the result of the build stage, the value should be either "SUCCESS" or "FAIL"

Here is a usage example
```
publishBuildRecord gitBranch: "${GIT_MASTER}", gitCommit: "${GIT_COMMIT}", gitRepo: "https://github.com/xunrongl/DemoDRA-1", result:"SUCCESS"
```

Note: Currently, jenkins pipeline does not expose git information to the environment variables, you can get git commit using `sh(returnStdout: true, script: 'git rev-parse HEAD').trim()`

### 2. publishTestResult
Publish the test result to the IBM Cloud DevOps, there are 2 required parameters:

1. type - the accepted values are
    1. `unittest` for unit test
    2. `fvt` for functional verification test
    3. `code` for code coverage
2. fileLocation - the result file location

Here is a usage example
```
publishTestResult type:'unittest', fileLocation: './mochatest.json'
publishTestResult type:'code', fileLocation: './tests/coverage/reports/coverage-summary.json'
```

### 3. publishDeployRecord
Publish the deploy record to the IBM Cloud DevOps, there are 2 required and 1 optional parameters:

1. (required) environment - the environment that you deploy your app to, if you deploy your app to the staging environment, use "STAGING"; if it is production environment, use "PRODUCTION"
2. (required) result  - the result of the build stage, the value should be either "SUCCESS" or "FAIL"
3. (optional) appUrl - the application url that you deploy your app to
Here is a usage example
```
publishDeployRecord environment: "STAGING", appUrl: "http://staging-Weather-App.mybluemix.net", result:"SUCCESS"
```

### 4. evaluateGate
Use IBM Cloud DevOps Gate in the pipeline, there is 1 required and 1 optional parameters:

1. (required) policy - the policy name that you define in the DevOps Insight
2. (optional) forceDecision - if you want to abort the pipeline based on the gate decision, set it to be `true`. It is `false` by default if you don't pass the parameter
Here is a usage example
```
evaluateGate policy: 'Weather App Policy', forceDecision: 'true'
```

### 5. notifyOTC
Configure your Jenkins jobs to send notifications to your Bluemix Toolchain by following the instructions in the [Bluemix Docs](https://console.ng.bluemix.net/docs/services/ContinuousDelivery/toolchains_integrations.html#jenkins). (Please disregard steps 8.d, 8.e, and 8.f because these are tailored for freestyle jobs.)

Publish the status of your pipeline stages to your Bluemix Toolchain:

1. (required) stageName - the name of the current pipeline stage.
2. (required) status - the completion status of the current pipeline stage. ('SUCCESS', 'FAILURE', and 'ABORTED' will be augmented with color)
3. (optional) webhookUrl - the webhook obtained from the Jenkins card on your toolchain.

#### Declarative Pipeline Example:
```
stage('Deploy') {
    steps {
      ...
    }

    post {
        success {
            notifyOTC stageName: "Deploy", status: "SUCCESS"
        }
        failure {
            notifyOTC stageName: "Deploy", status: "FAILURE"
        }
    }
}
```

#### Scripted Pipeline Example:
```
stage('Deploy') {
  try {
      ... (deploy steps)

      notifyOTC stageName: "Deploy", status: "SUCCESS"
  }
  catch (Exception e) {
      notifyOTC stageName: "Deploy", status: "FAILURE"
  }
}
```

#### Optional
In both cases you can override the IBM_CLOUD_WEBHOOK_URL:
```
notifyOTC stageName: "Deploy", status: "FAILURE", webhookUrl: "https://different-webhook-url@devops-api.ng.bluemix.net/v1/toolint/messaging/webhook/publish"
```

### 6. Traceability
Configure your Jenkins jobs to create a deployable mapping and send traceability information to your Bluemix Toolchain by following the instructions in steps 8.a and 8.b of the [Bluemix Docs](https://console.ng.bluemix.net/docs/services/ContinuousDelivery/toolchains_integrations.html#jenkins).

We recommend that you run `cf icd --create-connection ...` just after a deploy. Please note that you must be logged into cf and targeting an org an space before running `cf icd --create-connection ...`.  Here is an example:

<pre>
sh '''
    echo "CF Login..."
    cf api https://api.ng.bluemix.net
    cf login -u $IBM_CLOUD_DEVOPS_CREDS_USR -p $IBM_CLOUD_DEVOPS_CREDS_PSW -o $IBM_CLOUD_DEVOPS_ORG -s staging
    echo "Deploying...."
    export CF_APP_NAME="staging-$IBM_CLOUD_DEVOPS_APP_NAME"
    cf delete $CF_APP_NAME -f
    cf push $CF_APP_NAME -n $CF_APP_NAME -m 64M -i 1
    <b>cf icd --create-connection $IBM_CLOUD_DEVOPS_WEBHOOK_URL $CF_APP_NAME</b>
'''
</pre>
