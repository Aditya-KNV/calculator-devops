// Declarative CI/CD pipeline - Jenkins on WSL2 (Linux agent with Docker + Docker Compose v2).
// Jenkins prerequisites (see README section 5):
//   - Credential "dockerhub-credentials" (Username with password; password = Docker Hub access token)
//   - jenkins user in the "docker" group
//   - JDK 17+ on the agent (Maven itself comes from ./mvnw)
//   - Email Extension plugin + SMTP configured

pipeline {
    agent any

    parameters {
        string(name: 'DOCKERHUB_USER', defaultValue: 'your-dockerhub-username',
               description: 'Docker Hub namespace the image is pushed to')
    }

    environment {
        IMAGE_NAME   = "${params.DOCKERHUB_USER}/calculator-api"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        HOST_PORT    = '8081'                      // Nginx host port (Jenkins itself uses 8080)
        NOTIFY_EMAIL = 'knvaditya06@gmail.com'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    triggers {
        githubPush()                 // GitHub webhook (preferred)
        pollSCM('H/5 * * * *')       // fallback if the webhook cannot reach Jenkins
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x mvnw && git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
                success {
                    archiveArtifacts artifacts: 'target/calculator-api.jar', fingerprint: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t $IMAGE_NAME:$IMAGE_TAG -t $IMAGE_NAME:latest .'
            }
        }

        stage('Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                                  usernameVariable: 'DH_USER',
                                                  passwordVariable: 'DH_TOKEN')]) {
                    sh '''
                        echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin
                        docker push $IMAGE_NAME:$IMAGE_TAG
                        docker push $IMAGE_NAME:latest
                    '''
                }
            }
            post {
                always { sh 'docker logout || true' }
            }
        }

        stage('Deploy') {
            steps {
                // Idempotent: `up -d` only recreates containers whose image/config changed,
                // and --wait blocks until every service reports healthy.
                sh '''
                    docker compose up -d --no-build --remove-orphans --wait --wait-timeout 180
                    docker compose ps
                '''
                // Verify the deployed stack end-to-end through Nginx
                sh '''
                    BASE=http://localhost:$HOST_PORT
                    curl -fsS $BASE/actuator/health | grep -q '"status":"UP"'
                    curl -fsS "$BASE/api/v1/add?a=2&b=3" | tee /dev/stderr | grep -q '"result":5.0'
                    code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/divide?a=1&b=0")
                    [ "$code" = "400" ] || { echo "Expected 400 for divide by zero, got $code"; exit 1; }
                    echo "Deployment verified: $IMAGE_NAME:$IMAGE_TAG is live at $BASE"
                '''
            }
            post {
                failure { sh 'docker compose logs --tail=100 || true' }
            }
        }
    }

    post {
        success {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                body: """<p>Build <b>#${env.BUILD_NUMBER}</b> of <b>${env.JOB_NAME}</b> succeeded.</p>
                         <p>Image: <code>${IMAGE_NAME}:${IMAGE_TAG}</code> deployed at http://localhost:${HOST_PORT}</p>
                         <p><a href="${env.BUILD_URL}">Open build</a></p>"""
            )
        }
        failure {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                attachLog: true,
                body: """<p>Build <b>#${env.BUILD_NUMBER}</b> of <b>${env.JOB_NAME}</b> failed.</p>
                         <p>Console log attached. <a href="${env.BUILD_URL}console">Open console</a></p>"""
            )
        }
        always {
            sh 'docker image prune -f || true'    // remove dangling layers from repeated builds
        }
    }
}
