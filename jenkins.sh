echo '************** GET SL AGENT **********'
wget -nv https://agents.sealights.co/sealights-java/sealights-java-latest.zip
unzip -oq sealights-java-latest.zip

echo '************** GENERATE TOKEN FILE **********'

#echo '************** GENERATE SESSION ID **********'
#java -jar sl-build-scanner.jar -config -tokenfile ${WORKSPACE}/sltoken.txt -appname $JOB_NAME -branchname $BRANCH -buildname $BUILD_NUMBER -pi "com.khartec.*" 


#update the java process maven is starting to include the test listener java options

echo '************** SCAN BUILD **********'
echo '{
  "tokenFile": "'${WORKSPACE}'/sltoken.txt",
  "createBuildSessionId": true,
  "appName": "'${JOB_NAME}'",
  "branchName": "'${BRANCH}'",
  "buildName": "'${BUILD_NUMBER}'",
  "packagesIncluded": "*com.khartec.waltz.*",
  "includeResources": true,
  "executionType": "full",
  "testStage": "Unit Tests",
  "sealightsJvmParams": {
    "sl.featuresData.enableLineCoverage": "true"
  },
  "failsafeArgLine": "@{sealightsArgLine} -Dsl.testStage=\"Integration Tests\""
}' > slmaven.json
java -jar sl-build-scanner.jar -pom -configfile slmaven.json -workspacepath "."
#java -jar sl-build-scanner.jar -scan -tokenfile sltoken.txt -buildsessionidfile buildSessionId.txt -workspacepath "." -r

echo '*************** BUILD ****************'
mvn -Dmaven.test.failure.ignore=true clean install -P waltz-mariadb,dev-maria
java -jar sl-build-scanner.jar -restore -workspacepath "."

#If I were to break up the test stage section:
#Start test stage (here I will define the testStage)
#Run tests
#mvn test
#End test session

#This will be done automatically unless I am doing multiple modules
#java -jar sl-build-scanner.jar -buildend  -ok -tokenfile sltoken.txt -buildsessionidfile buildSessionId.txt

echo 'Downloading the Node.js agent file:'
npm i slnodejs

#echo 'Using Node.js Agent - Generating a session ID:'
./node_modules/.bin/slnodejs config --tokenfile ${WORKSPACE}/sltoken.txt --appname "$JOB_NAME"_fe --branch $BRANCH --build $BUILD_NUMBER

#- Using Node.js Agents - Front End application
# Need new buildsessionID because we are building another component, the frontend component
echo 'SeaLights Node.js agent - Scanning and Instrumentation of a Front-End app'
./node_modules/.bin/slnodejs build --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId --labid ${LAB_ID} --instrumentForBrowsers --workspacepath ${WORKSPACE}/waltz-ng/dist/ --outputpath ${WORKSPACE}/waltz-ng/sl_dist --scm git 

pwd
echo '-SeaLights Node.js agent - Deploying Instrumented code'
docker image build -t waltz .
pwd
docker stop waltz || true && docker rm waltz || true
docker container run -it --publish 8081:8080 -d --name=waltz --env JAVA_OPTS="-javaagent:/root/sealights/sl-test-listener.jar -Dsl.labId=${LAB_ID}" waltz
sleep 30
docker exec waltz sh -c 'mv /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static_bak'
docker exec waltz sh -c 'cp -r /root/sealights/sl_dist /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static'

echo 'Start Testing FE'
./node_modules/.bin/slnodejs start --tokenfile ${WORKSPACE}/sltoken.txt --labid ${LAB_ID} --teststage "Unit Tests"
docker exec waltz sh -c 'npm run test'
echo 'Upload test results?'
echo 'End Testing FE'
./node_modules/.bin/slnodejs end --tokenfile ${WORKSPACE}/sltoken.txt --labid ${LAB_ID}
