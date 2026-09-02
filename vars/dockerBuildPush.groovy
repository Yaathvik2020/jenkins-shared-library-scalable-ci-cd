def call(Map config) {
    sh "docker build -t ${config.registry}/${config.imageName}:${config.tag} ."
    withCredentials([usernamePassword(credentialsId: 'docker-registry-creds', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
        sh "echo \$PASS | docker login ${config.registry} -u \$USER --password-stdin"
        sh "docker push ${config.registry}/${config.imageName}:${config.tag}"
    }
}
