# hello-app — Java CI/CD with Jenkins, Docker and Kubernetes

A small Spring Boot REST app with a full pipeline:

```
Git push ─► Jenkins ─► Build & Test (Maven) ─► Docker Build ─► Docker Push (Docker Hub) ─► Deploy (kubectl) ─► Verify rollout
```

See `docs/cicd-flow.png` for the diagram.

| Path | What it is |
|---|---|
| `src/main/java/.../HelloController.java` | REST endpoints `/` and `/hello?name=` |
| `src/test/java/.../HelloControllerTest.java` | JUnit 5 + MockMvc tests (run in CI) |
| `pom.xml` | Maven build, Spring Boot 3.3, Java 17 → `target/app.jar` |
| `Dockerfile` | Multi-stage: Maven build image → small JRE runtime image, non-root |
| `k8s/namespace.yaml` | Namespace `demo` |
| `k8s/deployment.yaml` | 2 replicas, rolling update, health probes, CPU/memory limits |
| `k8s/service.yaml` | NodePort service on port `30080` |
| `Jenkinsfile` | The CI/CD pipeline |

---

## Pipeline stages

| # | Stage | What happens | Fails when |
|---|---|---|---|
| 1 | **Checkout** | Jenkins pulls the commit from Git | Repo/branch not reachable |
| 2 | **Build & Test** | `mvn clean verify` inside a `maven:3.9-eclipse-temurin-17` container; compiles, runs unit tests, publishes JUnit report | Compile error or a failing test |
| 3 | **Docker Build** | Builds image `yourdockerhubuser/hello-app:<BUILD_NUMBER>` and `:latest` | Dockerfile error |
| 4 | **Docker Push** | Logs in with `dockerhub-creds` and pushes both tags | Wrong credentials / no network |
| 5 | **Deploy to Kubernetes** | Uses `kubeconfig` credential; applies namespace, deployment (image tag filled in), service | Cluster not reachable / bad YAML |
| 6 | **Verify Rollout** | `kubectl rollout status` waits until new pods pass readiness probes | New pods crash or never get ready |

Every build gets a unique image tag (the Jenkins build number), so each deploy is traceable and can be rolled back.

---

## Step 1 — Install the tools (Ubuntu / Linux Mint)

You need: **Git, Docker, Java 17, Jenkins, kubectl, minikube** (local Kubernetes).
Skip anything already installed.

```bash
# Git + Java 17 (Jenkins itself needs Java 17 or 21)
sudo apt update
sudo apt install -y git openjdk-17-jdk curl

# Docker (if not installed): https://docs.docker.com/engine/install/ubuntu/
# On Linux Mint, use the UBUNTU codename (e.g. focal / jammy / noble) in the Docker repo, not the Mint one.
sudo usermod -aG docker $USER          # run docker without sudo (log out and in again)

# Jenkins: https://www.jenkins.io/doc/book/installing/linux/#debianubuntu
sudo apt install -y jenkins
sudo systemctl enable --now jenkins

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl

# minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube && rm minikube-linux-amd64
```

Check:

```bash
docker version && kubectl version --client && minikube version && java -version
```

---

## Step 2 — Run the app locally (optional, to see it work)

With Maven installed:

```bash
mvn clean verify            # build + tests
java -jar target/app.jar    # start
curl localhost:8080/        # {"message":"Hello from hello-app","version":"dev"}
curl "localhost:8080/hello?name=Vertoz"
```

Or with Docker only (no Maven needed):

```bash
docker build -t hello-app:local .
docker run --rm -p 8080:8080 hello-app:local
curl localhost:8080/
```

---

## Step 3 — Start Kubernetes and make a kubeconfig for Jenkins

```bash
minikube start --driver=docker --cpus=2 --memory=4096
kubectl get nodes                       # should show "minikube   Ready"
```

Jenkins runs as the `jenkins` user and can't read the certificate files in your home folder,
so export a **self-contained** kubeconfig (certificates embedded):

```bash
kubectl config view --flatten --minify > kubeconfig-jenkins.yaml
```

You'll upload `kubeconfig-jenkins.yaml` to Jenkins in Step 5. (It's a secret — don't commit it; `.gitignore` already excludes it.)

> Using a real cluster (EKS, GKE, on-prem)? Use a kubeconfig for a service account that can only manage the `demo` namespace, instead of an admin one.

---

## Step 4 — Push the project to Git

1. Create an empty repo on GitHub/GitLab, e.g. `hello-app`.
2. In the `Jenkinsfile`, change `DOCKER_REPO` to your Docker Hub username:
   `DOCKER_REPO = 'yourdockerhubuser/hello-app'`
3. Push:

```bash
cd java-cicd-demo
git init -b main
git add .
git commit -m "hello-app with Jenkins/Docker/Kubernetes pipeline"
git remote add origin https://github.com/<you>/hello-app.git
git push -u origin main
```

---

## Step 5 — Set up Jenkins

### 5.1 Unlock and install plugins

1. Open **http://localhost:8080** (if your app uses 8080 too, stop the app first).
2. Get the password: `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`
3. Choose **Install suggested plugins**, create the admin user.
4. **Manage Jenkins → Plugins → Available**, install:
   - **Docker Pipeline**
   - **Pipeline**, **Git**, **Credentials Binding**, **JUnit**, **Timestamper** (usually already there)

### 5.2 Let Jenkins use Docker

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
sudo -u jenkins docker ps        # must work without "permission denied"
sudo -u jenkins kubectl version --client
```

### 5.3 Add credentials

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

| ID (must match exactly) | Kind | Value |
|---|---|---|
| `dockerhub-creds` | Username with password | Docker Hub username + an **access token** (Docker Hub → Account settings → Personal access tokens) |
| `kubeconfig` | Secret file | Upload `kubeconfig-jenkins.yaml` from Step 3 |
| `github-creds` *(only for a private repo)* | Username with password | GitHub username + personal access token |

### 5.4 Create the pipeline job

1. **New Item** → name `hello-app` → **Pipeline** → OK
2. **Build Triggers**: tick **Poll SCM**, schedule `H/2 * * * *` (checks Git every ~2 min).
   *Use a GitHub webhook instead if your Jenkins is reachable from the internet.*
3. **Pipeline** section:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**, Repository URL: your repo URL, Credentials: `github-creds` (private repo only)
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
4. **Save**.

---

## Step 6 — Run the pipeline

Click **Build Now**. Watch **Console Output** (or the stage view). A green build shows:

```
Deployed yourdockerhubuser/hello-app:1 to namespace demo
```

Check the app:

```bash
kubectl -n demo get deploy,pods,svc
minikube service hello-app -n demo --url     # prints e.g. http://192.168.49.2:30080
curl http://192.168.49.2:30080/              # {"message":"Hello from hello-app","version":"1"}
```

`version` = Jenkins build number, so you can see which build is live.

---

## Step 7 — See CI/CD in action

1. Edit `HelloController.java`, e.g. change `"Hello from hello-app"` to `"Hello from hello-app v2"`.
2. Update the expected text in `HelloControllerTest.java` too.
3. `git commit -am "v2" && git push`
4. Within ~2 minutes Jenkins starts a build, tests it, builds/pushes image `:2`, and rolls it out with **zero downtime**
   (`maxUnavailable: 0` keeps old pods until new ones are ready).
5. `curl` again → `"version":"2"`.

**Test the safety net:** change the message but *not* the test, then push. The build stops at **Build & Test**, and nothing is deployed.

---

## Rollback

```bash
kubectl -n demo rollout history deployment/hello-app
kubectl -n demo rollout undo deployment/hello-app                 # previous version
kubectl -n demo rollout undo deployment/hello-app --to-revision=3 # specific revision
```

Or point the deployment straight at an older image tag:

```bash
kubectl -n demo set image deployment/hello-app hello-app=yourdockerhubuser/hello-app:5
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `permission denied ... /var/run/docker.sock` | `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins` |
| `Invalid agent type "docker"` | Install the **Docker Pipeline** plugin |
| `No such DSL method 'timestamps'` | Install the **Timestamper** plugin (or remove `timestamps()`) |
| `kubectl: not found` | Install kubectl into `/usr/local/bin` (Step 1) |
| `Unable to connect to the server` / certificate file not found | Re-create the kubeconfig with `--flatten`, re-upload the `kubeconfig` credential; make sure `minikube status` is Running |
| `denied: requested access to the resource is denied` on push | `DOCKER_REPO` must start with *your* Docker Hub username; check `dockerhub-creds` token has write access |
| Pods `ImagePullBackOff` | Image name/tag wrong, or repo is private (make it public, or add an `imagePullSecret`) |
| Pods `CrashLoopBackOff` | `kubectl -n demo logs deploy/hello-app` |
| `rollout status` times out | Readiness probe failing: `kubectl -n demo describe pod <pod>` |
| Port 8080 clash (Jenkins vs app) | Run the app locally with `java -jar target/app.jar --server.port=9090` |

---

## Taking it further

- **Branches:** use a *Multibranch Pipeline* and add `when { branch 'main' }` to the deploy stages, so feature branches only build and test.
- **Environments:** add `k8s/` overlays (Kustomize) or a Helm chart for dev / staging / prod, with an `input` approval step before prod.
- **Quality gates:** add SonarQube analysis and an image scan (e.g. Trivy) before the push.
- **Private registry:** swap Docker Hub for Harbor, ECR, GCR or Nexus; only `DOCKER_REPO` and the credential change.
