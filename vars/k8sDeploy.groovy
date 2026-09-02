def call(Map config) {
    /*   k8s need to deploy 
    sh """
        kubectl set image deployment/${config.imageName} \
        ${config.imageName}=${config.registry}/${config.imageName}:${config.tag} \
        -n ${config.namespace}
    """
    */
}
