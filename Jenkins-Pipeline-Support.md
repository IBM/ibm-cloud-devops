To use IBM Cloud DevOps with the Jenkins pipeline project, you can follow the [declarative Jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Declarative-Jenkinsfile) or [scripted jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Scripted-Jenkinsfile)

## Prerequisites
Make sure you are using Jenkins 2.X and have all pipeline related plugins installed and updated to the latest version
It has been test for Jenkins pipeline job with Pipeline plugin 2.5 version.

## 1. Create a Pipeline Job

Create a pipeline job, and configure it, there are 2 ways that you can do

1. In the Pipeline section, choose Pipeline script from SCM, and set up the SCM that your pipeline wants to run on and specify the Path of the Jenkinsfile. You can use the [declarative Jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Declarative-Jenkinsfile) as an example
2. Set up the SCM in the general configuration, then in the Pipeline section, choose Pipeline script, and put your scripted Jenkinsfile script there. You can use the [scripted jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Scripted-Jenkinsfile) as an example


## 2. Expose the required environment variables to all steps
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


## 3. Use the IBM Cloud DevOps steps
We provide 4 steps to upload the build/test/deploy information and use the IBM Cloud DevOps Gate

### 1. publishBuildRecord
Publish the build record to the IBM Cloud DevOps, there are 4 required parameters:

1. gitBranch - the name of the git branch
2. gitCommit - the commit id of the repo
3. gitRepo - the url of the git repo
4. result - the result of the build stage, the value should be either "SUCCESS" or "FAIL"

Here is a usage example
```
publishBuildRecord gitBranch: "${GIT_MASTER}", gitCommit: "${GIT_COMMIT}", gitRepo: "https://github.com/xunrongl/DemoDRA-1", result:"SUCCESS"
```

Note: Currently, jenkins pipeline does not expose git information to the environment variables, you can get git commit using `sh(returnStdout: true, script: 'git rev-parse HEAD').trim()`

### 2. publishTestResult
Publish the test result to the IBM Cloud DevOps, there are 2 required parameters:

1. type - the accepted values currently are only 3:
    1. `unittest` for unit test
    2. `fvt` for functional verification test
    3. `code` for code coverage
2. fileLocation - the result file location

Here is a usage example
```
publishTestResult type:'unittest', fileLocation: './mochatest.json'
publishTestResult type:'code', fileLocation: './tests/coverage/reports/coverage-summary.json'
```

### 3. publishSQResults

Configure a SonarQube scanner and a SonarQube server by following the instructions in the [SonarQube docs](https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Jenkins).

Publish the results of your latest SonarQube scan to IBM Cloud DevOps, there are 3 required parameters

1. (required) SQHostURL - the host portion of your SonarQube scanner's URL. This is extracted from the configuration above.
2. (required) SQAuthToken - your SonarQube API authentication token. This is extracted from the configuration above.
3. (required) SQProjectKey - the project key of the SonarQube project you wish to scan.

Here is a usage example
```
stage ('SonarQube analysis') {
    steps {
        script {
            def scannerHome = tool 'Default SQ Scanner';
            withSonarQubeEnv('Default SQ Server') {
               
                env.SQ_HOSTNAME = SONAR_HOST_URL;
                env.SQ_AUTHENTICATION_TOKEN = SONAR_AUTH_TOKEN;
                env.SQ_PROJECT_KEY = "My Project Key";
                
                run SonarQube scan ...
            }
        }
    }
}
stage ("SonarQube Quality Gate") {
     steps {
        ...
     }
     post {
        always {
            publishSQResults SQHostURL: "${SQ_HOSTNAME}", SQAuthToken: "${SQ_AUTHENTICATION_TOKEN}", SQProjectKey:"${SQ_PROJECT_KEY}"
        }
     }
}
```

### 4. publishDeployRecord
Publish the deploy record to the IBM Cloud DevOps, there are 2 required and 1 optional parameters:

1. (required) environment - the environment that you deploy your app to, if you deploy your app to the staging environment, use "STAGING"; if it is production environment, use "PRODUCTION"
2. (required) result  - the result of the build stage, the value should be either "SUCCESS" or "FAIL"
3. (optional) appUrl - the application url that you deploy your app to

Here is a usage example
```
publishDeployRecord environment: "STAGING", appUrl: "http://staging-Weather-App.mybluemix.net", result:"SUCCESS"
```

### 5. evaluateGate
Use IBM Cloud DevOps Gate in the pipeline, there is 1 required and 1 optional parameters:

1. (required) policy - the policy name that you define in the DevOps Insight
2. (optional) forceDecision - if you want to abort the pipeline based on the gate decision, set it to be `true`. It is `false` by default if you don't pass the parameter

Here is a usage example
```
evaluateGate policy: 'Weather App Policy', forceDecision: 'true'
```

### 6. notifyOTC
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
In both cases you can override the IBM_CLOUD_DEVOPS_WEBHOOK_URL:
```
notifyOTC stageName: "Deploy", status: "FAILURE", webhookUrl: "https://different-webhook-url@devops-api.ng.bluemix.net/v1/toolint/messaging/webhook/publish"
```

### 7. Traceability
Configure your Jenkins environment to create a deployable mapping message in order to send traceability information to your bluemix toolchain and track code deployments through tags, labels, and comments in your Git repository (repo) by following the instructions below.

#### Add the space environment variable
Traceability requires an additional space environment variable defined as follow:

`IBM_CLOUD_DEVOPS_SPACE` - the bluemix space where your application is deployed.

#### Send deployable mapping message
Simply add the following line to you deploy stage:
	sendDeployableMessage status: "SUCCESS"

#### Optional
You can override the `IBM_CLOUD_DEVOPS_WEBHOOK_URL`:

```
sendDeployableMessage status: "SUCCESS", webhookUrl: "https://different-webhook-url@devops-api.ng.bluemix.net/v1/toolint/messaging/webhook/publish"

#### Important
Add this line only when stage status is "SUCCESS". Any other status will be discarded.

#### Scripted Pipeline Example:
```
stage('Deploy') {
  try {
      ... (deploy steps)

      notifyOTC stageName: "Deploy", status: "SUCCESS"
      sendDeployableMessage status: "SUCCESS"
  }
  catch (Exception e) {
      notifyOTC stageName: "Deploy", status: "FAILURE"
  }
}
```
