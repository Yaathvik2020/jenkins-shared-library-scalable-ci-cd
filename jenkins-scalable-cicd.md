# Scalable CI/CD Setup Guide — Jenkins

A hands-on, step-by-step guide for going from "I've never built a pipeline" to a real-world, scalable Jenkins setup.

---

## Part 0: The Core Idea

CI/CD is one loop:

```
Push code → Automatic build → Automatic test → (Automatic) Deploy
```

- **CI (Continuous Integration)**: every code change is automatically built and tested, catching bugs in minutes instead of weeks.
- **CD (Continuous Delivery/Deployment)**: once a change passes, it's automatically shipped — either to a click-to-approve stage (Delivery) or fully automatically (Deployment).

**Scalability** means many teams and many pipelines can reuse the same platform and logic, instead of every team reinventing it:

| Concept | Purpose |
|---|---|
| Platform layer | Shared runners/agents, secrets, artifact registry, observability |
| Reusable pipeline templates | Write pipeline logic once, consume it everywhere |
| Isolated execution | Teams don't block or break each other's builds |
| Environment promotion | Same artifact promoted dev → staging → prod, not rebuilt per stage |
| Approval gates | Human sign-off before risky stages (like production) |
| Observability | Dashboards/alerts so failures are visible across all pipelines |

---

## Jenkins Basics (Self-Hosted CI/CD)

Jenkins follows the same loop, but **you run the server yourself**. This teaches you the "platform layer" concept directly, since you own the runner infrastructure.

### Step 1 — Install Jenkins locally (Docker)

```bash
docker run -d -p 8080:8080 -p 50000:50000 --name jenkins \
  -v jenkins_home:/var/jenkins_home jenkins/jenkins:lts
```

Open `http://localhost:8080`, unlock with the password from `docker logs jenkins`, install the suggested plugins.

### Step 2 — Create your first job
- **New Item → Pipeline**, name it.
- Under Pipeline, choose "Pipeline script":

```groovy
pipeline {
    agent any
    stages {
        stage('Hello') {
            steps {
                echo 'Hello, my first Jenkins pipeline!'
            }
        }
    }
}
```

- **Save → Build Now**, watch it turn green in the build history.

### Step 3 — Connect it to your GitHub repo
Commit a `Jenkinsfile` in your repo root:

```groovy
pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Install & Test') {
            steps {
                sh 'npm install'
                sh 'npm test'
            }
        }
        stage('Deploy') {
            when { branch 'main' }
            steps {
                echo 'Deploying...'
            }
        }
    }
}
```

In the job config, set **Pipeline → Definition** to "Pipeline script from SCM," point at your repo, script path `Jenkinsfile`.

### Step 4 — Set up a Jenkins Shared Library

A Shared Library is a separate Git repo of reusable pipeline code — Jenkins's version of a reusable workflow.

**4a. The full structure Jenkins expects**

```
jenkins-shared-library/
├── vars/
│   ├── standardPipeline.groovy
│   └── standardBuildTest.groovy
├── src/
│   └── org/
│       └── example/
│           └── Utils.groovy
├── resources/
│   └── org/
│       └── example/
│           └── config-template.json
└── README.md
```

- **`vars/`** — global functions, callable directly from any `Jenkinsfile` (e.g. `standardPipeline(...)`). Start here; this is 90% of what you need.
- **`src/`** — regular Groovy classes, for logic too complex for a single function (optional, add later).
- **`resources/`** — static files (JSON, XML, shell scripts, templates) loaded at runtime via `libraryResource()`. Optional, add only when needed.

Jenkins looks for these exact folder names, so getting the layout right matters more than anything else here.

**4a-i. What goes inside each file**

*`vars/standardPipeline.groovy`* — the main reusable pipeline entry point, the function a project's `Jenkinsfile` calls directly (`standardPipeline('my-app')`):

```groovy
def call(String appName) {
    pipeline {
        agent any
        stages {
            stage('Checkout') {
                steps { checkout scm }
            }
            stage('Install & Test') {
                steps {
                    echo "Testing ${appName}"
                    sh 'npm install'
                    sh 'npm test'
                }
            }
        }
    }
}
```
The function name **must match the filename** (`standardPipeline.groovy` → `def call(...)` invoked as `standardPipeline(...)`). Jenkins auto-loads every `.groovy` file in `vars/` as a global variable/function.

*`vars/standardBuildTest.groovy`* — a second, smaller reusable step, separate from the full pipeline, so it can be called individually inside a custom `Jenkinsfile` stage (useful for the "checkout stays local, build/test is shared" pattern from Step 4g):

```groovy
def call(Map config) {
    sh 'npm install'
    sh 'npm test'
    echo "Deploying ${config.appName}..."
}
```

*`src/org/example/Utils.groovy`* — a plain Groovy **class** (not a global function), for logic more complex than a single 
`vars/` function — helper methods, shared constants, reusable calculations. It's imported explicitly rather than called globally:

```groovy
package org.example

class Utils {
    static String buildTag(String appName, String buildNumber) {
        return "${appName}-${buildNumber}"
    }
}
```
Used from a `Jenkinsfile` like:
```groovy
import org.example.Utils
def tag = Utils.buildTag('my-app', env.BUILD_NUMBER)
```
The folder path `src/org/example/` **must match the `package org.example` line** — that's how Jenkins/Groovy resolves the class.

*`resources/org/example/config-template.json`* — a static file, not code, loaded at runtime with `libraryResource()`. Used for config templates, shell scripts, or boilerplate files you don't want hardcoded as Groovy strings:

```json
{
  "buildTool": "npm",
  "testCommand": "npm test",
  "defaultEnvironment": "staging"
}
```
Loaded from a `vars/` function like:
```groovy
def configText = libraryResource 'org/example/config-template.json'
def config = readJSON text: configText
```

*`README.md`* — plain documentation, not read by Jenkins at all, purely for humans. Explains what functions exist in `vars/`, how to call them, and what parameters they expect:

```markdown
# Jenkins Shared Library

## Available functions
- `standardPipeline(appName)` — full pipeline: checkout, install, test
- `standardBuildTest(config)` — build/test only, use when checkout is handled separately

## Usage
@Library('shared-lib') _
standardPipeline('my-app')
```

**Summary of the pattern**: `vars/` = things you *call*, `src/` = things you *import as classes*, `resources/` = things you *load as data*, `README.md` = things a *human reads*.

**4a-ii. Create it hands-on**

Create the folders locally:

```bash
mkdir jenkins-shared-library
cd jenkins-shared-library
mkdir -p vars
mkdir -p src/org/example
mkdir -p resources/org/example
```

Add your first reusable function:

```bash
cat > vars/standardPipeline.groovy << 'EOF'
def call(String appName) {
    pipeline {
        agent any
        stages {
            stage('Checkout') {
                steps { checkout scm }
            }
            stage('Install & Test') {
                steps {
                    echo "Testing ${appName}"
                    sh 'npm install'
                    sh 'npm test'
                }
            }
        }
    }
}
EOF
```

(Optional) add a helper class in `src/`:

```bash
cat > src/org/example/Utils.groovy << 'EOF'
package org.example

class Utils {
    static String buildTag(String appName, String buildNumber) {
        return "${appName}-${buildNumber}"
    }
}
EOF
```

The package path (`org/example`) must match the `package` declaration inside the file — standard Groovy/Java convention, and Jenkins relies on it to find the class.

Add a README (good practice, not required by Jenkins):

```bash
echo "# Jenkins Shared Library — reusable pipeline steps" > README.md
```

Init git and push to a real repo (create the empty repo on GitHub first):

```bash
git init
git add .
git commit -m "Initial shared library structure"
git branch -M main
git remote add origin https://github.com/your-org/jenkins-shared-library.git
git push -u origin main
```

Verify the structure is right:

```bash
find . -type f -not -path './.git/*'
```

You should see:
```
./vars/standardPipeline.groovy
./src/org/example/Utils.groovy
./README.md
```

This repo is now ready to be registered in Jenkins under **Manage Jenkins → System → Global Trusted Pipeline Libraries** (Step 4c below).

**4b. Write the reusable pipeline function**

`vars/standardPipeline.groovy`:

```groovy
def call(String appName) {
    pipeline {
        agent any
        stages {
            stage('Checkout') {
                steps { checkout scm }
            }
            stage('Install & Test') {
                steps {
                    echo "Testing ${appName}"
                    sh 'npm install'
                    sh 'npm test'
                }
            }
            stage('Deploy') {
                when { branch 'main' }
                steps {
                    echo "Deploying ${appName}..."
                }
            }
        }
    }
}
```

**4c. Register the library in Jenkins**
**Manage Jenkins → System → Global Trusted Pipeline Libraries**:
- Name: `shared-lib`
- Default version: `main`
- Retrieval method: Modern SCM → Git → your library repo URL

**4d. Use it from a project's Jenkinsfile**

```groovy
@Library('shared-lib') _
standardPipeline('my-app')
```

Two lines — Jenkins pulls `standardPipeline.groovy` from the library and runs it with `appName = "my-app"`.

**4e. Practice "fix once, applies everywhere"**
1. Create a second project repo with the same two-line `Jenkinsfile`, different app name.
2. Edit `standardPipeline.groovy` in the library repo — add a new stage (e.g. linting).
3. Trigger both project builds — both pick up the new stage automatically.

**4f. Parameterize further**

```groovy
def call(Map config) {
    pipeline {
        agent any
        stages {
            stage('Install & Test') {
                steps {
                    sh "npm install"
                    sh "npm test"
                }
            }
            stage('Deploy') {
                when { branch 'main' }
                steps {
                    echo "Deploying ${config.appName} to ${config.env ?: 'staging'}"
                }
            }
        }
    }
}
```

```groovy
@Library('shared-lib') _
standardPipeline(appName: 'my-app', env: 'production')
```

Projects now pass in config instead of editing shared code — the real-world pattern.

**4g. Reusing stages when projects use different SCMs**

Different projects often live on different SCM systems (Git, SVN, Bitbucket, Perforce). The clean way to keep stages reusable across all of them:
 **the shared library never touches SCM — checkout stays in each project's own `Jenkinsfile`, and only build/test/deploy logic is shared.**

```groovy
// Project A (Git) — Jenkinsfile
@Library('shared-lib') _
pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps { checkout scm }   // uses whatever SCM this job is configured with
        }
        stage('Build & Test') {
            steps { standardBuildTest(appName: 'my-app') }
        }
    }
}
```

```groovy
// Project B (SVN) — Jenkinsfile
@Library('shared-lib') _
pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout([$class: 'SubversionSCM', locations: [[remote: 'https://svn.example.com/repo/trunk']]])
            }
        }
        stage('Build & Test') {
            steps { standardBuildTest(appName: 'my-app') }
        }
    }
}
```

`standardBuildTest` in the library only contains build/test/deploy logic — it doesn't care where the code came from, because by the time it runs, the code is already on disk:

```groovy
// vars/standardBuildTest.groovy
def call(Map config) {
    sh 'npm install'
    sh 'npm test'
    echo "Deploying ${config.appName}..."
}
```

If you truly want the library to own checkout too (a single config-driven entry point across SCM types), pass an `scmType` and branch on it inside the library:

```groovy
// vars/standardPipeline.groovy (extended)
stage('Checkout') {
    steps {
        script {
            if (config.scmType == 'git') {
                checkout([$class: 'GitSCM',
                    branches: [[name: config.branch ?: 'main']],
                    userRemoteConfigs: [[url: config.repoUrl, credentialsId: config.credentialsId]]
                ])
            } else if (config.scmType == 'svn') {
                checkout([$class: 'SubversionSCM', locations: [[remote: config.repoUrl]]])
            } else {
                error "Unsupported SCM type: ${config.scmType}"
            }
        }
    }
}
```

```groovy
@Library('shared-lib') _
standardPipeline(scmType: 'git', repoUrl: 'https://github.com/org/my-app.git', credentialsId: 'github-creds', branch: 'main')
```

| | Checkout stays local (recommended) | Checkout centralized in library |
|---|---|---|
| Complexity | Low | Higher (branching logic to maintain) |
| Flexibility per project | High | Medium — bound by what the library supports |
| Best for | Most real teams | One config-driven entry point across many SCM types |

Most production setups keep checkout local — it keeps the library simple and lets each project's SCM quirks live where they belong.

###**4h. Real-time scenario: multiple microservices sharing one library**

This ties everything above together the way a real team would use it.

**Scenario**: TechCorp runs three microservices, each on a different stack:
- `payment-service` — Node.js
- `inventory-service` — Java (Maven)
- `notification-service` — Python

All three need the same pipeline shape: checkout → language-specific build & test → Docker build & push → deploy to Kubernetes (namespace decided by branch) → Slack notification on pass or fail.

Library structure for this scenario:

```
jenkins-shared-library/
└── vars/
    ├── microservicePipeline.groovy
    ├── dockerBuildPush.groovy
    ├── k8sDeploy.groovy
    └── notifySlack.groovy
```

`vars/microservicePipeline.groovy` — the entry point every service calls:

```groovy
def call(Map config) {
    pipeline {
        agent any
        stages {
            stage('Checkout') {
                steps { 
				checkout scm 
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
                    dockerBuildPush(imageName: config.appName, tag: env.BUILD_NUMBER, registry: config.registry)
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
```

`vars/dockerBuildPush.groovy`:
```groovy
def call(Map config) {
    sh "docker build -t ${config.registry}/${config.imageName}:${config.tag} ."
    withCredentials([usernamePassword(credentialsId: 'docker-registry-creds', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
        sh "echo \$PASS | docker login ${config.registry} -u \$USER --password-stdin"
        sh "docker push ${config.registry}/${config.imageName}:${config.tag}"
    }
}
```

`vars/k8sDeploy.groovy`:
```groovy
def call(Map config) {
    sh """
        kubectl set image deployment/${config.imageName} \
        ${config.imageName}=${config.registry}/${config.imageName}:${config.tag} \
        -n ${config.namespace}
    """
}
```

`vars/notifySlack.groovy`:
```groovy
def call(Map config) {
    def color = (config.status == 'SUCCESS') ? 'good' : 'danger'
    slackSend(channel: '#ci-cd-alerts', color: color, message: "${config.appName} build ${config.status}: ${env.BUILD_URL}")
}
```

Every service's `Jenkinsfile` is now one line of config — this is the real payoff of a shared library at scale:

 ###  microservicePipeline seperate the  job will be created for each service as the pipeline is involved

```groovy
 Pipeline job-1 for payment-service
// payment-service (Node.js)
@Library('shared-lib') _
microservicePipeline(appName: 'payment-service', lang: 'node', registry: 'registry.techcorp.com')
```

```groovy
Pipeline job-3 for payment-service
// inventory-service (Java)
@Library('shared-lib') _
microservicePipeline(appName: 'inventory-service', lang: 'java', registry: 'registry.techcorp.com')
```

```groovy
Pipeline job-2 for payment-service
// notification-service (Python)
@Library('shared-lib') _
microservicePipeline(appName: 'notification-service', lang: 'python', registry: 'registry.techcorp.com')
```

Why this is production-realistic, not a toy example:
- **Different languages, one pipeline shape** — the `lang` branch handles it, so the pipeline doesn't fork per stack.
- **Branch-based environment routing** — `main` → production, `develop` → staging, automatically, no manual job duplication.
- **Docker + registry + Kubernetes** — the actual deploy mechanism most companies use, not a placeholder "echo deploying" step.
- **Slack notification in `post { always {...} }`** — runs regardless of pass/fail, so teams get visibility without checking Jenkins manually.
- **Adding a 4th service** (say, a Go service) means adding one `else if` branch in `microservicePipeline.groovy` and a one-line Jenkinsfile — not rebuilding a pipeline from scratch.

---

###1. The library isn't registered at all (most common)

Go to Manage Jenkins → System (older versions) or Manage Jenkins → System Configuration → 
scroll to Global Trusted Pipeline Libraries (or Global Pipeline Libraries).

If nothing's listed there, that's the problem — add it:

Name: shared-lib (must match exactly what's in @Library('shared-lib'), case-sensitive)
Default version: main
Retrieval method: Modern SCM → Git
Project Repository: your actual library repo URL
## Real-World Scalable Jenkins Setup

The Shared Library above makes pipelines *reusable*, but reusability alone doesn't make Jenkins *scale*. Scaling comes from a different concept: **a single Jenkins server (the controller) doesn't run builds itself — it delegates them to separate worker machines (agents).** This section walks through that, step by step, in the order it actually matters.

### Step A — Understand controller vs agent
Jenkins has two roles:
- **Controller**: the web UI and brain — schedules jobs, holds configuration.
- **Agent**: a separate machine (or container/pod) that actually runs the build.

A controller that runs builds itself does not scale. Real setups always offload execution to agents.

### Step B — Run the controller
This is the Jenkins instance you already have running via Docker (Step 1 above). Its only job going forward is to coordinate — not to build.

### Step C — Add a static agent
Connect one extra machine as an agent over SSH, so builds run there instead of on the controller. This is the smallest possible "scale out" step.

1. Spin up a container that just has SSH and Java:
```bash
docker run -d --name jenkins-agent -p 2222:22 \
  jenkins/ssh-agent:latest "your-public-ssh-key-here"
```
2. In Jenkins: **Manage Jenkins → Nodes → New Node**, name it `agent-1`, type "Permanent Agent."
3. Set **Launch method**: "Launch agents via SSH," point it at the agent container's IP and port 2222, add the SSH credentials.
4. In your `Jenkinsfile`, change `agent any` to:
```groovy
agent { label 'agent-1' }
```
Now that pipeline runs on the agent, not the controller.

### Step D — Switch to on-demand Docker agents
Instead of keeping agent machines running 24/7, configure Jenkins to spin up a fresh Docker container per build and destroy it after. This is real elasticity — you stop paying for idle capacity. (Install the **Docker plugin**, then configure a Docker Cloud under **Manage Jenkins → Clouds**.)

### Step E — Move to Kubernetes agents (real elasticity)
This is the setup actually used in production — no idle machines, capacity grows and shrinks with demand.

1. Install the **Kubernetes plugin** (**Manage Jenkins → Plugins**).
2. Configure a Kubernetes Cloud (**Manage Jenkins → Clouds → Add a new cloud → Kubernetes**), pointing at your cluster (a free local one like `minikube` or `kind` is fine to practice on).
3. In your `Jenkinsfile`, define a pod template inline instead of a static label:

```groovy
pipeline {
    agent {
        kubernetes {
            yaml '''
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                  - name: node
                    image: node:20
                    command: ['cat']
                    tty: true
            '''
        }
    }
    stages {
        stage('Test') {
            steps {
                container('node') {
                    sh 'npm install && npm test'
                }
            }
        }
    }
}
```

4. Trigger a build — Jenkins creates a fresh pod, runs the build, then deletes the pod. Run 5 builds at once and watch 5 pods appear in parallel (`kubectl get pods`) — that's the "scalable" part made visible.

### Step F — Use Multibranch Pipelines
Instead of manually creating a Jenkins job per branch or per repo, use a **Multibranch Pipeline** job that auto-discovers branches/repos and runs the `Jenkinsfile` found in each. This is what makes managing many repos practical.

### Step G — Centralize with the Shared Library
Once multiple Multibranch pipelines exist, move common logic into the Shared Library repo (Step 4 above) so every team's `Jenkinsfile` stays a few lines, pulling shared logic from one place.

### Step H — Isolate teams with Folders
Group jobs into **Folders** per team, each with its own permissions and credentials. This prevents Team A's pipeline from seeing Team B's secrets, and keeps the job list manageable as pipeline count grows.

### Step I — Automate controller config (JCasC)
Define the entire Jenkins controller setup — plugins, credentials structure, agent config — in a YAML file using **Jenkins Configuration as Code (JCasC)**. This means you can destroy and recreate your whole Jenkins setup from a file instead of manually re-clicking settings — the same "infrastructure as code" idea applied to Jenkins itself.

**Why this order matters**: the Shared Library makes pipelines *reusable*; the controller/agent split (Steps A–E) is what makes them *scale*. You need both, but the agent concept comes first — it's the foundation the rest of this section builds on.

---

## GitHub Actions ↔ Jenkins Concept Map

Useful if you're learning both tools side by side:

| GitHub Actions | Jenkins |
|---|---|
| Reusable workflow | Shared Library |
| Runner (`ubuntu-latest`) | Agent (worker node — container, VM, or Kubernetes pod) |
| Environments + approval gates | `input` step (`input message: 'Deploy to prod?'`) |
| Secrets | Jenkins Credentials store, via `withCredentials` |

---

## Scaling Beyond the Basics

1. **Isolate blast radius** — separate agents per team so one broken pipeline doesn't starve others; set concurrency limits per project.
2. **Build once, promote everywhere** — tag one artifact with a commit SHA and promote the *same* build through dev → staging → prod, rather than rebuilding per environment.
3. **Centralize secrets** — one secrets manager (Jenkins Credentials + Vault), never scattered per-pipeline secrets.
4. **Version your templates** — treat the Shared Library like a product: semver it, test changes before wide rollout, let teams pin versions.
5. **Observability across all pipelines** — dashboards for build duration, failure rate, and queue time across every team's pipeline, not just one.

---

## Quick Reference: Where to Practice Next

Try running builds inside Docker agents (`agent { docker { image 'node:20' } }`) or Kubernetes pods instead of directly on the Jenkins host — a natural next step once this guide feels comfortable.
