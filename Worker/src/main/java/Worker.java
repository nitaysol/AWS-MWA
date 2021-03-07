
import software.amazon.awssdk.services.sqs.model.Message;
import p_utils.*;

public class Worker {

    public static void main(String [] args){
        SqsHandler fromManager = new SqsHandler("ManagerToWorkers");
        SqsHandler toManager = new SqsHandler("WorkersToManager");
        S3Handler s3Handler = new S3Handler();
        Message message;
        while (true)
            try {
                message = fromManager.getWorkerTask();
                if (message != null) {
                    System.out.println("Worker has received a message:");
                    String [] message_content = message.body().split(" ");
                    String appID = message_content[0];
                    String msgID = message_content[1];
                    String operation = message_content[2];
                    String pdfURL = message_content[3];
                    System.out.println("appID - " +appID);
                    System.out.println("msgID - " +msgID);
                    System.out.println("op - " +operation);
                    System.out.println("PDF URL - " + pdfURL);
                    //result is a string FILETYPE or ERROR
                    String fileType = OP.performAction(operation, pdfURL);
                    String result = fileType;
                    //upload file if result is good
                    if(!fileType.startsWith("ERROR"))
                    {
                        s3Handler.uploadFileContent(appID, msgID + fileType,
                                "result" + fileType);
                        result = "SUCCESS " + operation + " " + pdfURL + " " +
                                Consts.getS3ObjectUrl(appID, msgID + fileType);
                    }
                    else
                    {
                        result = "FAILURE " + operation + " " + pdfURL + " " + fileType;
                    }
                    result = appID + " " + result;
                    //sends response to the Manager
                    toManager.sendMessage(result);
                    fromManager.deleteMessage(message.receiptHandle());
                }
            }
            catch (Exception ex) {
                System.out.println("Caught exception:\n" + ex.getMessage());
            }
    }
}



