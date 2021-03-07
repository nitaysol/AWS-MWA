package p_utils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public class Consts {
    public static AwsCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials
    .create("your_creds", "your_creds"));}

    public static String getS3ObjectUrl(String appID, String fileName)
    {
        return "https://nitayandomerbuckets-" + appID + ".s3.amazonaws.com/" + fileName;
    }
    public static final String amiId = "ami-076515f20540e6e0b";
    public static final String managerScript =
            "#!/bin/bash\n" +
                    "wget " + getS3ObjectUrl("elements","Manager.jar") + " -O Manager.jar\n" +
                    "java -jar Manager.jar \n" +
                    "shutdown -h now \n";
    public static final String workerScript =
            "#!/bin/bash\n" +
                    "wget " + getS3ObjectUrl("elements","Worker.jar") + " -O Worker.jar\n" +
                    "java -jar Worker.jar \n";
}
