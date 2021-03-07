import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.model.*;
import p_utils.*;

import java.io.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class LocalApp {
    private static Ec2Client ec2;
    private static S3Handler s3Handler;

    public static void main(String[] args){
        s3Handler = new S3Handler();
        ec2 = Ec2Client.builder()
                .credentialsProvider(Consts.credentials())
                .region(Region.US_EAST_1)
                .build();

        System.out.println("Generating app ID");
        String appID = "app" + UUID.randomUUID().toString();
        System.out.println(appID);
        //System.out.println("Uploading jar files into elements bucket(Constant bucket) debugging");
        //uploadFiles();
        System.out.println("Creating bucket for app");
        s3Handler.createBucket(appID);
        System.out.println("Uploading input text to app bucket");
        s3Handler.uploadFileContent(appID,"input.txt", args[0]);
        System.out.println("Creating connection queues between manager and app if not exists");
        SqsHandler fromManager = new SqsHandler("ManagerToApp"+appID);
        SqsHandler toManager = new SqsHandler("AppsToManager");
        createInstance();

        String toSend = appID + " " + args[2];

        if (args.length > 3 && args[3].equals("terminate")) {
            toSend += " terminate";
        }
        toManager.sendMessage(toSend);
        Message message;
        System.out.println("Waiting for manager Response");
        while (true)
            try {
                message = fromManager.getManagerResponse();
                if (message != null) {
                    System.out.println("Received. Output file has been genrated");
                    fromManager.deleteMessage(message.receiptHandle());
                    fromManager.deleteQueue();
                    ConvertTXTToHTML(s3Handler.downloadFile(appID, "summary.txt"), args[1]);

                    return ;

                }
            }
            catch (Exception ex) {
                System.out.println("Caught exception:\n" + ex.getMessage());
            }
    }

    private static void uploadFiles(){
        s3Handler.createBucket("elements");
        s3Handler.uploadFileContent("elements", "Manager.jar", "Manager.jar");
        s3Handler.uploadFileContent("elements", "Worker.jar", "Worker.jar");

    }

    private static void createInstance() {
        System.out.println("Checking existence of Manager Instance...");
        if ((getManagerInstanceID()) == null) {
            createManager();
            System.out.println("No active manager. Initializing one...");
        } else
            System.out.println("Manager Instance already exists, continuing...");
    }

    private static void createManager(){
        String userData = Base64.getEncoder().encodeToString(Consts.managerScript.getBytes());
        List<Tag> tags = new ArrayList<>(1);
        tags.add(Tag.builder().key("Type").value("Manager").build());
        RunInstancesRequest request = RunInstancesRequest.builder().imageId(Consts.amiId)
                .userData(userData).minCount(1).tagSpecifications(TagSpecification.builder()
                        .resourceType(ResourceType.INSTANCE).tags(tags).build())
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(1).instanceInitiatedShutdownBehavior(ShutdownBehavior.TERMINATE).build();
        ec2.runInstances(request).instances().get(0).instanceId();
    }

    private static String getManagerInstanceID() {
        List<Reservation> reservations = ec2.describeInstances(
                DescribeInstancesRequest.builder().filters(Filter.builder()
                        .name("tag:Type").values("Manager").build()).build())
                .reservations();
        for (Reservation res : reservations) {
            List<Instance> instances = res.instances();
            for (Instance inst : instances) {
                switch (inst.state().code()) {
                    case 0: // pending
                    case 16: // running
                        return inst.instanceId();
                }
            }
        }
        return null;
    }




    private static void ConvertTXTToHTML(ResponseInputStream output, String outputFileName) {
        String result = "<html><body><div><br>";
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(output));
        try {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] record = line.split(" ");
                Boolean success = record[0].equals("SUCCESS");
                String op = record[1]+": ";
                String inputPDF = "<a href=\""+record[2]+"\">"+record[2]+"</a> ";
                String outputPDF = (success ? "<a href=\""+record[3]+"\" style=\"color:green\">"+record[3]+"</a>" : record[3]);
                result += "<div style=\"color:" + (success ? "green\">" : "red\">") + op + inputPDF + outputPDF + "</div>";
            }
            result += ("<br></div></body></html>");
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName+".html"));
            writer.write(result);
            writer.close();
        } catch (Exception e) {
            System.out.println("An Error Occurred: "+e.getMessage());
        }

    }
}
