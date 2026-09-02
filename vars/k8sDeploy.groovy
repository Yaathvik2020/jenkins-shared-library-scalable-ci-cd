def call(Map config) {
    sh """
        kubectl set image deployment/${config.imageName} \
        ${config.imageName}=${config.registry}/${config.imageName}:${config.tag} \
        -n ${config.namespace}
    """
}
