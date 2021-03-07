package p_utils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.*;

public class S3Handler {
    private S3Client s3;
    public static final String bucketNamePrefix = "nitayandomerbuckets-";
    public S3Handler() {
        this.s3 = S3Client.builder().credentialsProvider(Consts.credentials()).region(Region.US_EAST_1).build();
    }
    public void uploadFileContent(String bucketName, String output, String input) {
        this.s3.putObject(PutObjectRequest.builder()
                        .bucket(bucketNamePrefix + bucketName).key(output).
                                acl(ObjectCannedACL.PUBLIC_READ).build(),
                Paths.get(input));
    }
    public void createBucket(String bucketName)
    {
        s3.createBucket(CreateBucketRequest.builder().bucket(bucketNamePrefix + bucketName).build());
    }
    public ResponseInputStream downloadFile(String bucketName, String fileToDownload)
    {
        return s3.getObject(GetObjectRequest.builder()
                        .bucket(bucketNamePrefix + bucketName)
                        .key(fileToDownload).build(),
                ResponseTransformer.toInputStream());
    }
    public void deleteBucket(String bucketName)
    {
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder().bucket(bucketNamePrefix + bucketName).build();
        ListObjectsResponse listObjectsResponse;


        listObjectsResponse = s3.listObjects(listObjectsRequest);
        for (S3Object s3Object : listObjectsResponse.contents()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketNamePrefix+bucketName).key(s3Object.key()).build());
        }


    }

}
