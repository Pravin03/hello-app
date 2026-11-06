// CI/CD pipeline: Checkout -> Build & Test -> Docker Build -> Docker Push -> Deploy to Kubernetes -> Verify
//
// Jenkins needs (see README.md):
//   Plugins:     Pipeline, Git, Docker Pipeline, Credentials Binding, JUnit, Timestamper
//   Credentials: dockerhub-creds (Username with password), kubeconfig (Secret file)
//   On the agent: docker, kubectl; the jenkins user is in the "docker" group

pipeline {
    agent any

    environment {
        // CHANGE THIS to your Docker Hub username (or registry/repo)
        DOCKER_REPO = 'pravin2110/hello-app'
        IMAGE_TAG   = "${env.BUILD_NUMBER}"
        IMAGE       = "${DOCKER_REPO}:${IMAGE_TAG}"
        K8S_NS      = 'demo'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build & Test') {
            // Runs Maven inside a container, so Jenkins needs no JDK/Maven installed
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-17'
                    reuseNode true
                }
            }
            steps {
                // Keep the Maven cache in the workspace so it is reused by the next build
                // and is writable by the jenkins user inside the container.
                sh 'mvn -B -Dmaven.repo.local="$WORKSPACE/.m2/repository" clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t "$IMAGE" -t "$DOCKER_REPO:latest" .
                    docker image ls "$DOCKER_REPO"
                '''
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'REG_USER',
                                                  passwordVariable: 'REG_PASS')]) {
                    sh '''
                        echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin
                        docker push "$IMAGE"
                        docker push "$DOCKER_REPO:latest"
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl apply -f k8s/namespace.yaml
                        sed -e "s|IMAGE_PLACEHOLDER|$IMAGE|" \
                            -e "s|VERSION_PLACEHOLDER|$IMAGE_TAG|" \
                            k8s/deployment.yaml | kubectl apply -f -
                        kubectl apply -f k8s/service.yaml
                    '''
                }
            }
        }

        stage('Verify Rollout') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl -n "$K8S_NS" rollout status deployment/hello-app --timeout=180s
                        kubectl -n "$K8S_NS" get pods -l app=hello-app -o wide
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Deployed ${IMAGE} to namespace ${K8S_NS}"
        }
        failure {
            echo 'Pipeline failed - check the stage logs above.'
            // Uncomment to roll back automatically if the deploy step broke the app:
            // withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
            //     sh 'kubectl -n demo rollout undo deployment/hello-app || true'
            // }
        }
        always {
            // Free disk space on the Jenkins machine
            sh 'docker image rm "$IMAGE" "$DOCKER_REPO:latest" || true'
        }
    }
}
