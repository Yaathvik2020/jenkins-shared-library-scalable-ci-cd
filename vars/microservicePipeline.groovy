def call(Map config) {
    pipeline {
        agent any
        stages {
            stage('Checkout') {
                steps { 
                    git branch: 'main' ,url: 'https://github.com/Yaathvik2020/jenkins-shared-library.git'
                }
            }
            stage('Build & Test') {
                steps {
                    script {
                        if (config.lang == 'node') {
                            sh 'npm install && npm test'
                        } else if (config.lang == 'java') {
                            sh 'mvn clean test'
                        } else if (config.lang == 'python') {
                            sh 'pip install -r requirements.txt && pytest'
                        } else {
                            error "Unsupported language: ${config.lang}"
                        }
                    }
                }
            }
            stage('Docker Build & Push') {
                steps {
                    dockerBuildPush(imageName: config.appName, tag: env.BUILD_NUMBER, dockerhubUser: config.dockerhubUser)
                }
            }
            stage('Deploy') {
                when { anyOf { branch 'main'; branch 'develop' } }
                steps {
                    script {
                        def ns = (env.BRANCH_NAME == 'main') ? 'production' : 'staging'
                        k8sDeploy(namespace: ns, imageName: config.appName, tag: env.BUILD_NUMBER)
                    }
                }
            }
        }
        post {
            always {
                notifySlack(appName: config.appName, status: currentBuild.currentResult)
            }
        }
    }
}
