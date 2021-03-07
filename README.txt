
Running the program:
1. extract the zip file.
2. go the to folder location via cd.
3. in shell run "cd out/artifacts"
4. in shell run "java -jar LocalApp.jar <inputFileName> <outputFileName> n <optional: terminate>

When: 
inputFileName = input(please add extension if needed - such as .txt
outputFileName = output file name(no extension
n = number of workers you want
terminate = terminate(optional) - if you would like this local application to be the last one and terminate the manager's process then run with terminat.

Example:
without terminate: java -jar LocalApp.jar input1.txt output1 100
with terminate: java -jar LocalApp.jar input1.txt output1 100 terminate

Progaram's resources:
Queues:
	AppsToManager queue- the queue that every local application will connect to when starting up,
						the information from the local apps to the manager will be transfered through this queue
	ManagerToApp queue - every local application has it's own personal queue with the manager to receive the
						reviews analysis back from the manager
	ManagerToWorkers queue - the manager uses this queue to send to the workers the reviews to analyze
	WorkersToManager queue - the workers use this queue to send back a message to the manager that
						the review analysis is done and the result has been uploaded to S3

Buckets:
	jar bucket - the bucket that contains the jar files and does not change while the program is running
	app bucket - app results bucket -> each app has its own bucket

Running description:
	The local application:
		1. checks if a manager is active and if not starts a new one
		2. creates the personal queue for the returning results fromt he manager
		3. the local application upload the input file into s3 into his own bucket.
		5. sends a message to the manager with the id of the specific app running(generated randomally) + number of tasks per worker
		6. waits for a message on the ManagerToApp queue indicating the process is done and the response (the summary file) is available on S3
		7. download the summary file from S3 and create an HTML file representing the results.
		8. if terminate was one of the arguments than it will send a termination message to the manager.
		9. deleting ManagerToApp queue.
	Workers:
		the worker is opened upon request from the manager.
		1. He waits for masseges from the manager in ManagerToWorkers queue
		2. upon receiving an msg perform the action needed and formating the pdf file.
		3. once finished sends a message to the manager in WorkersToManager queue with the details needed for the manager to update its fields.
	Manager:
		1. The manager firstly getting the queues url.
		2. creates a Thread for listening to workers respones
		3. listening to apps requests while not terminated
		4. upon receivingan app request creates another Thread which will handle the request and goback to step 3.
			4.1 the opened Thread will handle terminate if given -> change atomic boolean value to true;
		5. upon receiving terminate message it stops listening to apps requests and waiting for all jobs to be done.
		6. when all jobs are done terminates all workers and queues
		
Additional information:
	-The ami we use is - ami-076515f20540e6e0b
	-Both the Manager and Workers are T2_Micro
	-The n we used is 100

Additional questions:
	1. security - the credentials is passed through the user data script after it has been encoded to 64 bit, we do not upload the credentials to S3.
	2. Scalability - We noticed a place of improvement but the manager could handle a lot of inputs as we have "unlimited" workers
	3. persistance - every message passed on the a sqs queue is available only for one receiver for a specific time and if the receiver has failed to analyze the message it will be
	available for other receivers, this way we prevent from a message to be "lost" in the process.
	4. Threads in the application - our idea behind using threads is to not delay actions that can be done simultaniously. the manager for example can receive messages both
	from the locals and from the workers, therefore we used threads in the implementation of the manager.
	5. Termination - we validate our termination process, we are using a specific order of running + atomic boolean to verify when we encounter termination
	6. Parallel running- We ran our app from 2 different computers and the outputs were valid.
	7. System limitations - Deleting a bucket requires clearing it first + Creating instances is limited to 32 = we took it into account.
	8. Workers - Not all the workers process the same ammount of messages -> as mentioned in the assignment its ok -> its because all workers consume tasks from the same queue
	9. Manager - incharge if the transfer of the information and the concluding of the results before they are passed back to the local applications -
	the manager does those tasks using different tasks that are being run by threads in order to prevent unneeded stalls.
	10. distributed - Ofc the manager must wait for messages in order to do something but we made out greatest effort the each part of the system wont cause delay.

Outputs:
1st input file: n = 100 => 3 minutes.
2nd input file: n = 100 => 15 seconds. 
Initialize new manager (If does not exist) => 77 seconds.