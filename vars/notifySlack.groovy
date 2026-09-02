def call(Map config) {
    /* need  stack  set up to use this 
    def color = (config.status == 'SUCCESS') ? 'good' : 'danger'
    slackSend(channel: '#ci-cd-alerts', color: color, message: "${config.appName} build ${config.status}: ${env.BUILD_URL}")
    */
}
