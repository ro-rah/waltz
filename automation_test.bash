#https://sealights.atlassian.net/wiki/spaces/SUP/pages/705200206/Running+Tests+Only+using+Sealights+Maven+plugin
echo '************** GET SL AGENT **********'
wget -nv https://agents.sealights.co/sealights-java/sealights-java-latest.zip
unzip -oq sealights-java-latest.zip

echo '************** CREATE TOKEN FILE **********'
echo 'eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL0RFVi1jcy5hdXRoLnNlYWxpZ2h0cy5pby8iLCJqd3RpZCI6IkRFVi1jcyxpLTBmNjIzNDA0ZDE0MTRkYWQxLEFQSUdXLWMxY2MzODU3LTFlNDItNGQwNy1iODU3LWU2ZTRhN2FjNTdmZCwxNjM1NDQzNjYxOTI5Iiwic3ViamVjdCI6IlNlYUxpZ2h0c0BhZ2VudCIsImF1ZGllbmNlIjpbImFnZW50cyJdLCJ4LXNsLXJvbGUiOiJhZ2VudCIsIngtc2wtc2VydmVyIjoiaHR0cHM6Ly9kZXYtY3MtZ3cuZGV2LnNlYWxpZ2h0cy5jby9hcGkiLCJzbF9pbXBlcl9zdWJqZWN0IjoiIiwiaWF0IjoxNjM1NDQzNjYxfQ.J75InYgwvcXdIz4Jir-Z8w6g833qJbUAS40KgzWjE32cActU8Bzu3fGZ78BaF-f4qikhPAXl9fzbcBYkBo8LB4EEUNyJ7x2jlZJqBP-SH2cvUsIsvoaKGm2-rEI7MqwhHWn7r9hwDFx2GclzLSKFTUeAw3W9nSlEYAj88jMAKAJthqmnWMyJXxWQ9wnqhjYInQXYzEyWlYzieGhfpVo6jScsZHSatlkwLFb5dhqY9eMYTZS6rMMRxEyE68pJaBrXmG_aC50J1yOZSeUpMflzusYPMSFuamTesx2LQ0ziK3BoFTmL85u6aFZD6etFJL0w63kVsNAHULfDxnb0fr0lnFzpitP7D8dWri1e8Kty_Mj9o3OyH-xqNsSVbibNaAvLymfUj5IhkUvUDM1joPnE-gUdgFEG9x2sa_A7RGxQV_n4jgkhFm3cIxPyM_dsiiXwzIfMIP1nY1LrABk7Ez2v1J4lQgcsHCy_5U_QXfeuhTLNnPLAG4WzWrqg49byyqP6oal69mgo922xgkb3PHAEnRHyNjyM6MGtAPEU4FERl_E7IaKkBzIa7d5lri5eWk3hmnKUSIfACxI-TznpPcoVFy-oG5XY8uO2XJmwMx7P33A6docE-NqaOyXnNU42ThvTz4C1SD8SV7-70akigbSo_Wd61ArBhPl96pw6gXdvkN4' > sltoken.txt

echo '************** CONFIG **********'
echo '{
  "tokenFile": "'${WORKSPACE}'/sltoken.txt",
  "createBuildSessionId": false,
  "executionType": "testsonly",
  "testStage": "Functional Tests",
  "labId": "integ_qa_d202_ronakwaltzver3_integration"
}' > slmaven.json

java -jar sl-build-scanner.jar -pom -configfile slmaven.json -workspacepath "."
mvn test -DrunFunctionalTests=true -Dmaven.test.failure.ignore=true
java -jar sl-build-scanner.jar -restore -workspacepath "."