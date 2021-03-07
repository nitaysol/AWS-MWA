package p_utils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;

public class SqsHandler {
    private SqsClient sqsClient;
    private String queueUrl;

    public SqsHandler(String queueName) {
        // changing name to have fifo queue
        createQueue(queueName);

    }
    public void createQueue(String queueName) {
        System.out.println("Creating " + queueName + " queue");
        try{
            //building queue with attributes of fifo
            this.sqsClient = SqsClient.builder().credentialsProvider(Consts.credentials()).region(Region.US_EAST_1).build();
            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();
            sqsClient.createQueue(createQueueRequest);
            //getting queue URL
            System.out.println("Get queue url");
            GetQueueUrlResponse getQueueUrlResponse =
                    sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
            this.queueUrl = getQueueUrlResponse.queueUrl();
            System.out.println(queueUrl);
        }
        catch(QueueDeletedRecentlyException ex){
            try {
                System.out.println("Waiting 60 seconds before creating a new queue");
                Thread.sleep(61000);
                createQueue(queueName);
            }
            catch(Exception e){}
        }

    }

    public Message getWorkerTask() {
        return receiveMessage(20);
    }
    public Message getManagerResponse() {
        return receiveMessage(10);
    }
    public Message receiveMessage(int visibility) {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .maxNumberOfMessages(1)
                .visibilityTimeout(visibility)
                .build();
        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
        return messages.size() > 0 ? messages.get(0) : null;
    }
    public Message receiveMessage() {
        //System.out.println("Checking Received Message in:" + this.queueUrl);
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .maxNumberOfMessages(1)
                .build();
        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
        return messages.size() > 0 ? messages.get(0) : null;
    }

    public void deleteMessage(String receiptHandle) {
        System.out.println("Delete Message");
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .receiptHandle(receiptHandle)
                .build();
        sqsClient.deleteMessage(deleteMessageRequest);
    }
    public void sendMessage(String msg)
    {
        System.out.println("\nSend Message\n");
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .messageBody(msg)
                .build());
    }
    public void deleteQueue(){
        System.out.println("\nDelete Queue\n");
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
    }
}