Install and fire up the Sandbox using the instructions here: http://maprdocs.mapr.com/home/SandboxHadoop/c_sandbox_overview.html. 

Use an SSH client such as Putty (Windows) or Terminal (Mac) to login. See below for an example:
use userid: user01 and password: mapr.

For VMWare use:  $ ssh user01@ipaddress 

For Virtualbox use:  $ ssh user01@127.0.0.1 -p 2222 

You can build this project with Maven using IDEs like Eclipse or NetBeans, and then copy the JAR file to your MapR Sandbox, or you can install Maven on your sandbox and build from the Linux command line, 
for more information on maven, eclipse or netbeans use google search. 

After building the project on your laptop, you can use scp to copy your JAR file from the project target folder to the MapR Sandbox:

use userid: user01 and password: mapr.
For VMWare use:  $ scp  nameoffile.jar  user01@ipaddress:/user/user01/. 

For Virtualbox use:  $ scp -P 2222 nameoffile.jar  user01@127.0.0.1:/user/user01/.  

Copy the data file from the project data folder to the sandbox using scp to this directory /user/user01/data/uber.csv on the sandbox:

For Virtualbox use:  $ scp -P 2222 data/uber.csv  user01@127.0.0.1:/user/user01/data/. 

This example runs on MapR 5.2.1 with Spark 2.1  

By default, Spark classpath doesn't contain spark-streaming-kafka-producer_2.11-2.1.0-mapr-1703.jar and spark-streaming-kafka-0-9_2.11-2.1.0-mapr-1703.jar
which you can get here:  
http://repository.mapr.com/nexus/content/groups/mapr-public/org/apache/spark/spark-streaming-kafka-0-9_2.11/2.1.0-mapr-1703/spark-streaming-kafka-0-9_2.11-2.1.0-mapr-1703.jar

http://repository.mapr.com/nexus/content/groups/mapr-public/org/apache/spark/spark-streaming-kafka-producer_2.11/spark-streaming-kafka-producer_2.11-2.1.0-mapr-1703.jar


There are 3 ways:
1. Build your application with spark-streaming-kafka-0-9_2.11.jar as dependencies. 
2. Manually download the jars and put to SparkHome/jars folder.
3. Manually download the jars and pass the path to spark submit script with --jars option
read more here http://maprdocs.mapr.com/home/Spark/Spark_IntegrateMapRStreams.html

note use:  mapr-sparkml-streaming-uber-1.0-jar-with-dependencies.jar if you did not update the spark classpath

Step 0: Create the topics to read from (ubers) and write to (uberp)  in MapR streams:

maprcli stream create -path /user/user01/stream -produceperm p -consumeperm p -topicperm p
maprcli stream topic create -path /user/user01/stream -topic ubers  
maprcli stream topic create -path /user/user01/stream -topic uberp  

to get info on the ubers topic :
maprcli stream topic info -path /user/user01/stream -topic ubers

to delete topics:
maprcli stream topic delete -path /user/user01/stream -topic ubers  
maprcli stream topic delete -path /user/user01/stream -topic uberp  
____________________________________________________________________

Step 1:  Run the Spark k-means program which will create and save the machine learning model: 

spark-submit --class com.sparkml.uber.ClusterUber --master local[2]  mapr-sparkml-streaming-uber-1.0.jar 

you can also copy paste  the code from  ClusterUber.scala into the spark shell 

$spark-shell --master local[2]
 
 - For Yarn you should change --master parameter to yarn-client - "--master yarn-client"

_________________________________________________________________________________  

Alternative Step 1: 

If you want to skip the machine learning and publishing part, the JSON results are in a file in the directory data/cluster.txt
scp this file to /user/user01/data/cluster.txt

Then run the Java producer to produce messages with the topic and data file arguments:

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgProducer /user/user01/stream:uberp /user/user01/data/cluster.txt

To run the MapR Streams Java consumer  with the topic to read from (uberp):

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgConsumer /user/user01/stream:uberp 

Then you can skip to Step 4 

____________________________________________________________________

After creating and saving the k-means model you can run the Streaming code:

Step 2: Run the MapR Streams Java producer to produce messages, run the Java producer with the topic (ubers) and data file arguments (uber.csv):

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgProducer /user/user01/stream:ubers /user/user01/data/uber.csv

Optional: run the MapR Streams Java consumer to see what was published :

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgConsumer /user/user01/stream:ubers


Step 3:  Run the Spark Consumer Producer with the arguments: path to model, subscribe topic, publish topic :
(in separate consoles if you want to run at the same time)

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumerProducer --master local[2] \
 mapr-sparkml-streaming-uber-1.0-jar-with-dependencies.jar /user/user01/data/savemodel  /user/user01/stream:ubers /user/user01/stream:uberp

____________________________________________________________________


Step 4: Run the Spark Consumer which consumes the enriched messages with the topic to read from (uberp):

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumer --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:uberp

____________________________________________________________________


Step 5:  Spark Streaming writing to MapR-DB HBase

Configure the HBase connector as documented here
http://maprdocs.mapr.com/home/Spark/ConfigureSparkHBaseConnector.html

start the hbase shell and create a table: 
hbase shell
create '/user/user01/db/uber', {NAME=>'data'}

Run the code to Read from MapR Streams and write to MapR-DB HBAse

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumerWriteHBase --master local[2] \
mapr-sparkml-streaming-uber-1.0-jar-with-dependencies.jar /user/user01/stream:uberp  "/user/user01/db/uber"

start the hbase shell and scan to see results: 
hbase shell
scan '/user/user01/db/uber' , {'LIMIT' => 5}

____________________________________________________________________


Step 6:  Spark Reading from  MapR-DB HBase


Run the code to Read from  MapR-DB HBAse into a spark dataframe

spark-submit --class com.sparkkafka.uber.SparkHBaseReadDF --master local[2] \
mapr-sparkml-streaming-uber-1.0-jar-with-dependencies.jar "/user/user01/db/uber"

_________________________________________________________________________________

to delete the ubers topic after using :
maprcli stream topic delete -path /user/user01/stream -topic ubers

_________________________________________________________________________________

Other examples included:  
from https://github.com/mapr/spark/tree/2.1.0-mapr-1703/examples/src/main/scala/org/apache/spark/examples/streaming

Spark Streaming Consuming from MapR-Streams:

spark-submit --class com.sparkkafka.uber.V09DirectKafkaWordCount --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:ubers

Spark Streaming  publishing to  MapR-Streams:
spark-submit --class com.sparkkafka.uber.KafkaProducerExample  --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:ubert




