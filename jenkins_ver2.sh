echo '************** GET SL AGENT **********'
wget -nv https://agents.sealights.co/sealights-java/sealights-java-latest.zip
unzip -oq sealights-java-latest.zip

echo '************** GENERATE TOKEN FILE **********'
echo 'eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL0RFVi1jcy5hdXRoLnNlYWxpZ2h0cy5pby8iLCJqd3RpZCI6IkRFVi1jcyxpLTBmNjIzNDA0ZDE0MTRkYWQxLEFQSUdXLWMxY2MzODU3LTFlNDItNGQwNy1iODU3LWU2ZTRhN2FjNTdmZCwxNjM1NDQzNjYxOTI5Iiwic3ViamVjdCI6IlNlYUxpZ2h0c0BhZ2VudCIsImF1ZGllbmNlIjpbImFnZW50cyJdLCJ4LXNsLXJvbGUiOiJhZ2VudCIsIngtc2wtc2VydmVyIjoiaHR0cHM6Ly9kZXYtY3MtZ3cuZGV2LnNlYWxpZ2h0cy5jby9hcGkiLCJzbF9pbXBlcl9zdWJqZWN0IjoiIiwiaWF0IjoxNjM1NDQzNjYxfQ.J75InYgwvcXdIz4Jir-Z8w6g833qJbUAS40KgzWjE32cActU8Bzu3fGZ78BaF-f4qikhPAXl9fzbcBYkBo8LB4EEUNyJ7x2jlZJqBP-SH2cvUsIsvoaKGm2-rEI7MqwhHWn7r9hwDFx2GclzLSKFTUeAw3W9nSlEYAj88jMAKAJthqmnWMyJXxWQ9wnqhjYInQXYzEyWlYzieGhfpVo6jScsZHSatlkwLFb5dhqY9eMYTZS6rMMRxEyE68pJaBrXmG_aC50J1yOZSeUpMflzusYPMSFuamTesx2LQ0ziK3BoFTmL85u6aFZD6etFJL0w63kVsNAHULfDxnb0fr0lnFzpitP7D8dWri1e8Kty_Mj9o3OyH-xqNsSVbibNaAvLymfUj5IhkUvUDM1joPnE-gUdgFEG9x2sa_A7RGxQV_n4jgkhFm3cIxPyM_dsiiXwzIfMIP1nY1LrABk7Ez2v1J4lQgcsHCy_5U_QXfeuhTLNnPLAG4WzWrqg49byyqP6oal69mgo922xgkb3PHAEnRHyNjyM6MGtAPEU4FERl_E7IaKkBzIa7d5lri5eWk3hmnKUSIfACxI-TznpPcoVFy-oG5XY8uO2XJmwMx7P33A6docE-NqaOyXnNU42ThvTz4C1SD8SV7-70akigbSo_Wd61ArBhPl96pw6gXdvkN4' > sltoken.txt

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

cd waltz-ng/
echo 'Front End Testing'
echo 'Downloading the Node.js agent file and dependecies'
npm i slnodejs
npm i mocha-junit-reporter

echo 'Using Node.js Agent - Generating a session ID:'
#./node_modules/.bin/slnodejs config --tokenfile ${WORKSPACE}/sealights/sltoken.txt --appname "ronak_waltz-ui" --branch "master" --build "2.${BUILD_NUMBER}"
./node_modules/.bin/slnodejs config --tokenfile ${WORKSPACE}/sltoken.txt --appname "$JOB_NAME"_fe --branch $BRANCH --build $BUILD_NUMBER

#- Using Node.js Agents - Front End application
# Need new buildsessionID because we are building another component, the frontend component
echo 'SeaLights Node.js agent - Scanning and Instrumentation of a Front-End app'
#./node_modules/.bin/slnodejs build --tokenfile ${WORKSPACE}/sealights/sltoken.txt --buildsessionidfile buildSessionId --instrumentForBrowsers --workspacepath dist --outputpath sl_dist --scm git --labid=${LAB_ID} --projectRoot $workspace/waltz-ng --usebranchcoverage false
./node_modules/.bin/slnodejs build --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId  --instrumentForBrowsers --workspacepath ${WORKSPACE}/waltz-ng/dist/ --outputpath ${WORKSPACE}/waltz-ng/sl_dist --scm git --labid ${LAB_ID} --projectRoot ${WORKSPACE}/waltz-ng --usebranchcoverage false


echo 'Start Testing FE'
#./node_modules/.bin/slnodejs start --tokenfile ${WORKSPACE}/sealights/sltoken.txt --buildsessionidfile buildSessionId --teststage "Unit Tests"
./node_modules/.bin/slnodejs start --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId --teststage "Unit Tests"

echo 'Customer Portion: Run FE unit tests'
./node_modules/.bin/nyc --reporter=json ./node_modules/.bin/mocha --compilers js:babel-core/register --recursive --reporter mocha-junit-reporter
#./node_modules/.bin/slnodejs mocha --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId --teststage "Unit Tests" --useslnode2 -- --recursive

echo 'TBD: Upload unit tests results'
#for uploading coverage:
./node_modules/.bin/slnodejs nycReport --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId
#for uploading tests, later in deployment I will have a SL agent running that will send the tests to sealights.
./node_modules/.bin/slnodejs uploadReports --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId --reportFile test-results.xml

echo 'End Testing FE'
#./node_modules/.bin/slnodejs end --tokenfile ${WORKSPACE}/sealights/sltoken.txt --buildsessionidfile buildSessionId
./node_modules/.bin/slnodejs end --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId

sleep 90
#make the integration build in the environmet -> grouping of components (its a sealights thing)
# need a new buildsessionId (the branch is really a new environment), this is done in line 67 of test, just a diff between branch 
#line 68 is defining the components
#line 69 build is similar to the scan command from js, we have a --dependecies file and sealights understands it is grouping compoentns we are not actively scanning at this time 
# app may be a component or a set of compoents (as in an integration build)
# NOW we will have a new "integration build" row in sealights app.

echo 'Start Integration Testing'
./node_modules/.bin/slnodejs config --tokenfile ${WORKSPACE}/sltoken.txt --appname "$JOB_NAME"_integration --branch "qa" --build "2.${BUILD_NUMBER}"
echo '************** Component List **********'
echo '[{"appName": "'${JOB_NAME}'","branchName": "'${BRANCH}'","buildName": "'${BUILD_NUMBER}'"},{"appName": "'${JOB_NAME}'_fe","branchName": "'${BRANCH}'",
  "buildName": "'${BUILD_NUMBER}'"}]' > dependencies.json

./node_modules/.bin/slnodejs build --tokenfile ${WORKSPACE}/sltoken.txt --buildsessionidfile buildSessionId --dependenciesFile dependencies.json --workspacepath style --scm git

#deploy application
cd ${WORKSPACE}
echo '-SeaLights Node.js agent - Deploying Instrumented code'
docker image build -t waltz .
docker stop waltz || true && docker rm waltz || true
#set up lab_id in the docker container
#docker container run -it --publish 8081:8080 -d --name=waltz --env JAVA_OPTS="-javaagent:/root/sl-test-listener.jar -Dsl.labId=${LAB_ID}" waltz
docker container run -it --publish 8081:8080 -d --name=waltz --env JAVA_OPTS="-javaagent:/root/sealights/sl-test-listener.jar -Dsl.labId=${LAB_ID}" waltz
sleep 30
docker exec waltz sh -c 'mv /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static_bak'
#docker exec waltz sh -c 'cp -r /root/waltz-ng/sl_dist /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static'
docker exec waltz sh -c 'cp -r /root/sealights/sl_dist /usr/local/tomcat/webapps/waltz-web/WEB-INF/classes/static'


#install chrome extension - install the token
#https://sealights.atlassian.net/wiki/spaces/SUP/pages/7869463/Installation+and+Setup+for+SeaLights+Chrome+extension
#run manual tests




#verify if integration build lab gets created automatically, I will have to get lab name
#line 75 -> sets labid (in the deployed env)
#line 60 -> because we don't have an agent