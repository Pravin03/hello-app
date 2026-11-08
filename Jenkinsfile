
pipeline {
    agent any

    environment {
        DOCKER_USER = 'pravin2110'
        IMAGE_NAME = 'cicd-demo'
        REGISTRY = 'docker.io'
        IMAGE_TAG = "${BUILD_NUMBER}"
        FULL_IMAGE = "${DOCKER_USER}/${IMAGE_NAME}:${BUILD_NUMBER}"
        KUBECONFIG = '/var/lib/jenkins/.kube/config'
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Checking out source code'

                checkout scm
            }
        }

        stage('Maven Clean') {
            steps {
                sh 'mvn clean'
            }
        }

        stage('Unit Testing') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Build Application') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                    docker build \
                    -t ${FULL_IMAGE} \
                    -t ${DOCKER_USER}/${IMAGE_NAME}:latest .
                """
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        echo "$DOCKER_PASSWORD" | \
                        docker login -u "$DOCKER_USERNAME" \
                        --password-stdin

                        docker push "$FULL_IMAGE"
                        docker push "$DOCKER_USER/$IMAGE_NAME:latest"

                        docker logout
                    '''
                }
            }
        }

        stage('Kubernetes Deploy') {
            steps {
                sh """
                    kubectl apply -f k8s/

                    kubectl set image \
                    deployment/cicd-demo \
                    cicd-demo=${FULL_IMAGE}

                    kubectl rollout status \
                    deployment/cicd-demo \
                    --timeout=180s
                """
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    kubectl get deployments
                    kubectl get pods
                    kubectl get services

                    kubectl rollout status \
                    deployment/cicd-demo \
                    --timeout=180s
                '''
            }
        }
    }

    post {
        success {
            echo 'CI/CD Pipeline completed successfully!'
        }

        failure {
            echo 'CI/CD Pipeline failed. Please check Jenkins logs.'
        }

        always {
            echo 'Pipeline execution completed.'
        }
    }
}

