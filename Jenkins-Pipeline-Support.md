To use IBM Cloud DevOps with the Jenkins pipeline project, you can follow the [Sample Jenkinsfile](https://github.ibm.com/oneibmcloud/Jenkins-IBM-Bluemix-Toolchains/blob/pipeline-support/Sample-Jenkinsfile)

## Prerequisites
Make sure you are using Jenkins 2.X and have all pipeline related plugins installed 
It has been test for Jenkins pipeline job with Pipeline plugin 2.5 version.

## 1. Expose the required environment variables to all steps
The plugin required 4 environment variables:

1. IBM_CLOUD_DEVOPS_CREDS - the bluemix credentials ID that you defined in the jenkins, e.g `IBM_CLOUD_DEVOPS_CREDS = credentials('BM_CRED')` 
2. IBM_CLOUD_DEVOPS_ORG - the bluemix org that you are going to use
3. IBM_CLOUD_DEVOPS_APP_NAME - the name of your application
4. IBM_CLOUD_DEVOPS_TOOLCHAIN_ID - the toolchain id that you are using, you can get the toolchain id from the url after the toolchain is created. e.g https://console.ng.bluemix.net/devops/toolchains/TOOLCHAIN_ID.

Here is an example to use it in the Jenkinsfile

```
environment {
        IBM_CLOUD_DEVOPS_CREDS = credentials('BM_CRED')
        IBM_CLOUD_DEVOPS_ORG = 'lix@us.ibm.com'
        IBM_CLOUD_DEVOPS_APP_NAME = 'Weather-V1-Xunrong'
        IBM_CLOUD_DEVOPS_TOOLCHAIN_ID = '1320cec1-daaa-4b63-bf06-7001364865d2'
    }
```
 
## 2. Use the IBM Cloud DevOps steps
We provide 4 steps to upload the build/test/deploy information and use the IBM Cloud DevOps Gate

### 1. publishBuildRecord
There are 4 required parameters:

1. gitBranch
2. gitCommit
3. gitRepo
4. result - the result of the build stage, the value should be either "SUCCESS" or "FAILED"

Here is a usage example
```
publishBuildRecord gitBranch: "${GIT_MASTER}", gitCommit: "${GIT_COMMIT}", gitRepo: "https://github.com/xunrongl/DemoDRA-1", result:"SUCCESS"
```

Note: Currently, jenkins pipeline does not expose git information to the environment variables, you can get git commit using `sh(returnStdout: true, script: 'git rev-parse HEAD').trim()`

### 2. publishTestResult
There are 2 required parameters:

1. type - the accepted values are
    1. `unittest` for unit test
    2. `fvt` for functional verifcation test
    3. `code` for code coverage
2. fileLocation - the result file location

Here is a usage example
```
publishTestResult type:'unittest', fileLocation: './mochatest.json'
publishTestResult type:'code', fileLocation: './tests/coverage/reports/coverage-summary.json'
```

### 3. publishDeployRecord
There are 4 required parameters:

1. environment - the environment that you deploy your app to, the value should be either "STAGING" or "PRODUCTION"
2. appUrl
3. result - the result of the build stage, the value should be either "SUCCESS" or "FAILED"
Here is a usage example
```
publishDeployRecord environment: "STAGING", appUrl: "http://staging-Weather-App.mybluemix.net", result:"SUCCESS"
```

### 4. evaluateGate
There is 1 required and 1 optional parameters:

1. (required) policy - the policy name that you define in the DevOps Insight
2. (optional) forceDecision - if you want to abort the pipeline based on the gate decision, set it to be `true`. It is `false` by default if you don't pass the parameter
Here is a usage example
```
evaluateGate policy: 'Weather App Policy', forceDecision: 'true'
```
