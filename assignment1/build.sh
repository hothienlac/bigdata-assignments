#!/bin/bash

# This build scripts automates the build process to run any application generated 
# via SAP Marketing Cloud SDK(powered by SAP S/4HANA SDK) on a Kubernetes Cluster 
# enhanced with SAP Kyma

function log() {
    local exp=$1;
    local color=$2;
    local NC='\033[0m'
    if ! [[ ${color} =~ '^[0-9]$' ]] ; then
       case $(echo ${color} | tr '[:upper:]' '[:lower:]') in
        red) color='\e[31m' ;;
        green) color='\e[32m' ;;
        yellow) color='\e[33m' ;;
        nc|*) color=${NC} ;; # no color or invalid color
       esac
    fi

    printf "${color}${exp}${NC}\n"
}

createInKyma()
{
    log "Deploying Container ${dockerImageName} to Kyma environment \"$1\"." green
    kubectl apply -f ./deployment.yaml -n $1;
}

applyToKyma()
{
	log "Currently this feature is not yet available, please use this Script for New Deployments" red
	exit
}

deployToKyma()
{
    read -p "Which Environment in Kyma, would you like to deploy this container? \n" kyma_env_to_deploy
    read -p "Have you ever deployed this application before on this environment? [Yes/No] " yn2
    case $yn2 in
        [Yy]* ) createInKyma $kyma_env_to_deploy ;;
        [Nn]* ) createInKyma $kyma_env_to_deploy ;;
    esac;
}

log "Initiating Build and Deploy to Kyma, Please ensure the following pre-requisites are full filled" yellow
log "1 Availablility Apache Maven Build Tool" yellow
log "2 Availability of Docker CLI Tools" yellow
log "3 Availability of Kubernetes CLI Tools and set the current server context to any Kyma Cluster" yellow

log "Step 1: Building your Maven Project" green

mvn clean install

log "Trying to perform docker login" yellow

docker login

log "Step 2: Building a docker image with current Source Code" green

docker build -t ${dockerImageName} .

echo "Step 3: Pushing docker image ${dockerImageName} to docker hub" green

docker push ${dockerImageName}

while true; do
	read -p "Do you want to deploy this container to kyma? [Yes/No] " yn
	case $yn in
		[Yy]* ) deployToKyma; break;;
		[Nn]* ) exit ;;
	esac
done

