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

This example runs on MapR 5.2 with Spark 2.0.1 . If running on the sandbox you need to upgrade to MEP 2.0,  Spark  2.0.1 
http://maprdocs.mapr.com/home/UpgradeGuide/UpgradingEcoPacks.html
http://maprdocs.mapr.com/home/Spark/Spark_IntegrateMapRStreams.html

By default, Spark classpath doesn't contain  "spark-streaming-kafka-0-9_2.11.jar".  
There are 3 ways:
1. Build your application with spark-streaming-kafka-0-9_2.11.jar as dependencies. 
2. Manually download spark-streaming-kafka-0-9_2.11.jar and put to SparkHome/jars folder.
3. Manually download spark-streaming-kafka-0-9_2.11.jar and  pass path to spark submit script with --jars option

Step 0: Create the topics to read from (ubers) and write to (uberp)  in MapR streams:

maprcli stream create -path /user/user01/stream -produceperm p -consumeperm p -topicperm p
maprcli stream topic create -path /user/user01/stream -topic ubers -partitions 3
maprcli stream topic create -path /user/user01/stream -topic uberp -partitions 3

to get info on the stream:
maprcli stream topic info -path /user/user01/stream -topic sensor ubers
to delete a topic after using :
maprcli stream topic delete -path /user/user01/stream -topic sensor ubers

____________________________________________________________________

Step 1:  Run the Spark k-means program which will create and save the machine learning model: 

spark-submit --class com.sparkml.uber.ClusterUber --master local[2]  mapr-sparkml-streaming-uber-1.0.jar 

you can also copy paste  the code from  ClusterUber.scala into the spark shell 

$spark-shell --master local[2]

There is also a notebook file, in the notebooks directory, which you can import and run in Zeppelin, or view in a Zeppelin viewer
    
 - For Yarn you should change --master parameter to yarn-client - "--master yarn-client"

____________________________________________________________________

After creating and saving the k-means model you can run the Streaming code:

Step 2: Run the MapR Streams Java producer to produce messages, run the Java producer with the topic (ubers) and data file arguments (uber.csv):

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgProducer /user/user01/stream:ubers /user/user01/data/uber.csv

To run the MapR Streams Java consumer to see what was published :

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgConsumer /user/user01/stream:ubers


Step 4:  Run the Spark Consumer Producer with the arguments: path to model, subscribe topic, publish topic :
(in separate consoles if you want to run at the same time)

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumerProducer --master local[2] \
 mapr-sparkml-streaming-uber-1.0.jar /user/user01/data/savemodel  /user/user01/stream:ubers /user/user01/stream:uberp

____________________________________________________________________


Step 5: Run the Spark Consumer which consumes and analyzes the machine learning enriched methods with the topic to read from (uberp):

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumer --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:uberp


To run the MapR Streams Java consumer  with the topic to read from (uberp):

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgConsumer /user/user01/stream:uberp 

_________________________________________________________________________________  

Alternative Step 1: 

If you want to skip the machine learning and publishing part, the JSON results are in a file in the directory data/ubertripclusters.json
scp this file to /user/user01/data/ubertripclusters.json

Then run the MapR Streams Java producer to produce messages with the Java producer with the topic and data file arguments:

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgProducer /user/user01/stream:uberp /user/user01/data/ubertripclusters.json

To run the MapR Streams Java consumer  with the topic to read from (uberp):

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgConsumer /user/user01/stream:uberp 

Then you can skip to Step 5

_________________________________________________________________________________

Other examples included:  
from https://github.com/mapr/spark/blob/2.0.1-mapr-1611/examples/src/main/scala/org/apache/spark/examples/streaming/

Spark Streaming Consuming from MapR-Streams:

/opt/mapr/spark/spark-2.0.1/bin/spark-submit --class com.sparkkafka.uber.V09DirectKafkaWordCount --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:ubers

Spark Streaming  publishing to  MapR-Streams:
spark-submit --class com.sparkkafka.uber.KafkaProducerExample  --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:ubert




