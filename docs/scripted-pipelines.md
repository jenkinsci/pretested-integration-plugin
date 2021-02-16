# Generating scripted pipelines in Jenkins
Jenkins has a syntax generator that can aide in generating scripted pipeline

#### The syntax generator
The syntax generator can generate the PiP setup for us, just follow these points:
- In the `sample step` choose `checkout: General SCM`
- use `Git` as `SCM`
- Fill in the url to your repository - Github, bitbucket, gitlab etc.
- Choose the set of credentials you use with your repo
- In `Branches to build` choose the `branch specifier` to be `*/ready/**`
- In `Additional Behaviours` choose `Use Pretested Integration`, that should give two extra options.
- As `integration branch` choose the name of your integration branch, normally it is `master`
- As `repository name` it should be `origin`
- Click `Generate Pipeline Script`

This should output a long line in the text field which you will use as the first step in the scripted pipeline. To push you changes add `pretestedIntegration()` after  the end of your job


### Simple pipeline script example
This is an example of a pipeline script with a single pipeline job that does Pretested Integration, a mvn install and pushes the changes to the integration branch after the previous steps are successful.

``` groovy
node {
   checkout([$class: 'GitSCM', branches: [[name: '*/ready/**']], extensions: [pretestedIntegration(pretestedIntegration: squash(), integrationBranch: 'master', repoName: 'origin')], userRemoteConfigs: [[credentialsId: 'GitHub', url: 'https://github.com/Praqma/phlow-test.git']]])
   sh 'mvn install'
   pretestedIntegrationPublisher()
}
```
