export REPOSITORY_RABBITMQ=$repo_service &&
export REPOSITORY_MAIN=$repo_main &&
apt-get update &&
apt-get install -y wget &&
wget $repo_service/rabbitmq-template.sh --no-cache &&
chmod +x rabbitmq-template.sh &&
./rabbitmq-template.sh -d $rabbit_vhost -u $rabbit_user -p $rabbit_password -e docker
