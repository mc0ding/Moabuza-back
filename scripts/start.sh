#!/usr/bin/env bash

# start.sh
# 서버 구동을 위한 스크립트

ABSPATH=$(readlink -f $0)
ABSDIR=$(dirname $ABSPATH)
source ${ABSDIR}/profile.sh

REPOSITORY=/home/ubuntu/app
REPOSITORY1=/home/ubuntu
PROJECT_NAME=moabuja-0.0.1-SNAPSHOT

echo "> 새 어플리케이션 배포"

JAR_NAME=$(ls -tr $REPOSITORY/*.jar | tail -n 1)

echo "> JAR Name: $JAR_NAME"

echo "> $JAR_NAME 에 실행권한 추가"

chmod +x $JAR_NAME

echo "> $JAR_NAME 실행 -==== 바뀐건가?"

IDLE_PROFILE=$(find_idle_profile)

echo "> $JAR_NAME 를 profile=$IDLE_PROFILE ====이거 모냐==== 로 실행합니다."
nohup java -jar \
    -Dspring.config.location=classpath:/application-$IDLE_PROFILE.yml \
    -Dspring.profiles.active=$IDLE_PROFILE $JAR_NAME > $REPOSITORY1/nohup.out 2>&1 &