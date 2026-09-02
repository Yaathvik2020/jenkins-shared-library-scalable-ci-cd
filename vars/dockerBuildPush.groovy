def call(Map config) {
    def image = "${config.dockerhubUser}/${config.imageName}:${config.tag}"
    sh "docker build -t ${image} ."
    withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
        sh "echo \$PASS | docker login -u \$USER --password-stdin"   // no registry URL needed for Docker Hub
        sh "docker push ${image}"
    }
}
