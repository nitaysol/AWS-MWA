import p_utils.Consts;
import p_utils.S3Handler;
import p_utils.SqsHandler;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class Manager {
    private static Ec2Client ec2;

    public static void main(String[] args)
    {
         ec2 = Ec2Client.builder()
                .credentialsProvider(Consts.credentials())
                .region(Region.US_EAST_1)
                .build();
        AtomicBoolean shouldTerminate = new AtomicBoolean(false);
        S3Handler s3Handler = new S3Handler();
        ConcurrentHashMap<String, Integer> appsMapMsgsCounter = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> appsMapResults = new ConcurrentHashMap<>();
        SqsHandler fromAPPs = new SqsHandler("AppsToManager");
        SqsHandler toWorkers = new SqsHandler("ManagerToWorkers");
        SqsHandler fromWorkers = new SqsHandler("WorkersToManager");

        //Tasks from Workers Thread.
        new Thread(() ->{
            Message msgFromWorker;
            while(!shouldTerminate.get() || !appsMapMsgsCounter.isEmpty())
            {
                msgFromWorker = fromWorkers.receiveMessage();
                if(msgFromWorker==null)
                {
                    //System.out.println("No message received from workers");
                }
                else
                {
                    System.out.println("recieved msg from worker: " + msgFromWorker.body());
                    fromWorkers.deleteMessage(msgFromWorker.receiptHandle());
                    String[] message_content = msgFromWorker.body().split(" ");
                    String appID = message_content[0];
                    appsMapResults.putIfAbsent(appID, "");
                    String currentResult = appsMapResults.get(appID);
                    //msg after appID
                    String newResult = currentResult + msgFromWorker.body().substring(msgFromWorker.body().indexOf(" ") + 1);
                    appsMapResults.put(appID, newResult + "\n");

                    appsMapMsgsCounter.computeIfPresent(appID, (key, oldValue) -> oldValue-1);
                    System.out.println("Current counter for app is"+ appsMapMsgsCounter.get(appID));
                    if (appsMapMsgsCounter.get(appID) == 0) {
                        System.out.println("Deleting app-"+appID);
                        createSummary(appID, appsMapResults.get(appID), s3Handler);
                        appsMapResults.remove(appID);
                        SqsHandler toApp = new SqsHandler("ManagerToApp" + appID);
                        toApp.sendMessage("complete");
                        appsMapMsgsCounter.remove(appID);
                    }
                }

            }
        }).start();


        Message msgFromApp;
        while(!shouldTerminate.get())
        {
            msgFromApp = fromAPPs.receiveMessage();
           if(msgFromApp==null)
            {
                //System.out.println("No message received from apps");
            }
            else {
               System.out.println("#####Message received from app#####");
               System.out.println(msgFromApp.body());
               fromAPPs.deleteMessage(msgFromApp.receiptHandle());
               String[] message_content = msgFromApp.body().split(" ");
               //Thread for handling apps request
               new Thread(() -> {
                   String appID = message_content[0];
                   int n = Integer.parseInt(message_content[1]);
                   ResponseInputStream input = s3Handler.downloadFile(appID, "input.txt");
                   BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));
                   String row;
                   int numerator = 0;
                   try {
                       Vector<String> msgsToSend = new Vector<>();
                       String[] content;
                       while ((row = bufferedReader.readLine()) != null) {
                           content = row.split("\t");
                           numerator++;
                           msgsToSend.add(appID + " " + numerator + " " + content[0] + " " + content[1]);
                       }
                       appsMapMsgsCounter.put(appID, numerator);

                       for(String s : msgsToSend)
                           toWorkers.sendMessage(s);
                       createWorkers(n, numerator);
                       if(message_content.length == 3 && message_content[2].equals("terminate"))
                       {
                           shouldTerminate.set(true);
                       }

                   } catch (Exception e) {
                       System.out.println("Error in Buffer Reader - "+e.getMessage());
                   }
               }).start();

           }


        }

        while(!shouldTerminate.get() || !appsMapMsgsCounter.isEmpty())
        { /*System.out.println("Waiting before termination");*/ }

        //Terminate queues
        fromAPPs.deleteQueue();
        fromWorkers.deleteQueue();
        toWorkers.deleteQueue();
        //Terminate workers
        ec2.terminateInstances(TerminateInstancesRequest.builder().instanceIds(getActiveWorkers()).build());
    }

    public static void createWorkers(int n, int numerator){
        System.out.println("Number of tasks per worker is: " + n);
        System.out.println("Number of tasks is:" + numerator);
        int numOfWorkers = (int)Math.ceil((double)numerator/n);
        System.out.println("Number of workers needed is: " + numOfWorkers);
        int k = countActiveWorkers();
        System.out.println("Number of active workers before update is: " + k);
        if(numOfWorkers-k>0){
            createWorkers(numOfWorkers - k);
        }
    }
    public static void createWorkers(int numToCreate){
        String userData = Base64.getEncoder().encodeToString(Consts.workerScript.getBytes());
        List<Tag> tags = new ArrayList<>(1);
        tags.add(Tag.builder().key("Type").value("Worker").build());
        RunInstancesRequest request = RunInstancesRequest.builder().imageId(Consts.amiId)
                .userData(userData).tagSpecifications(TagSpecification.builder()
                        .resourceType(ResourceType.INSTANCE).tags(tags).build())
                .instanceType(InstanceType.T2_MICRO)
                .minCount(numToCreate).maxCount(numToCreate).build();
        ec2.runInstances(request).instances().get(0).instanceId();
    }

    public static int countActiveWorkers() {
        return getActiveWorkers().size();
    }
    public static List<String> getActiveWorkers() {
        List<String> workersLst = new LinkedList<>();
        List<Reservation> reservations = ec2.describeInstances(
                DescribeInstancesRequest.builder().filters(Filter.builder()
                        .name("tag:Type").values("Worker").build()).build())
                .reservations();
        for (Reservation res : reservations) {
            List<Instance> instances = res.instances();
            for (Instance inst : instances) {
                switch (inst.state().code()) {
                    case 0:
                    case 16:
                        workersLst.add(inst.instanceId());
                }
            }
        }
        return workersLst;
    }
    public static void createSummary(String appID, String result, S3Handler s3Handler)
    {
        try {
            FileWriter myWriter = new FileWriter("summary"+appID);
            myWriter.write(result);
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
            s3Handler.uploadFileContent(appID,"summary.txt", "summary"+appID);

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
