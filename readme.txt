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

To Run the Spark k-means program which will create and save the machine learning model: 

spark-submit --class com.sparkml.uber.ClusterUber --master local[2]  mapr-sparkml-streaming-uber-1.0.jar 

you can also copy paste  the code from  ClusterUber.scala into the spark shell 

$spark-shell --master local[2]

There is also a notebook file, in the notebooks directory, which you can import and run in Zeppelin, or view in a Zeppelin viewer
    
 - For Yarn you should change --master parameter to yarn-client - "--master yarn-client"

______________________________________________________

After creating and saving the model you can run the Streaming code:

- Create the topics to read from and write to in MapR streams:

maprcli stream create -path /user/user01/stream -produceperm p -consumeperm p -topicperm p
maprcli stream topic create -path /user/user01/stream -topic ubers -partitions 3
maprcli stream topic create -path /user/user01/stream -topic uberp -partitions 3

To run the MapR Streams Java producer to produce messages, run the Java producer with the topic and data file arguments:

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgProducer /user/user01/stream:ubers /user/user01/data/uber.csv

To Run the Spark Consumer Producer (in separate consoles if you want to run at the same time) run the spark consumer with the topic to read from and write to:

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumerProducer --master local[2] mapr-sparkml-streaming-uber-1.0.jar  /user/user01/stream:ubers /user/user01/stream:uberp

To Run the Spark Consumer which consumes the machine learning enriched methods run the spark consumer with the topic to read from and write to:

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumer --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:uberp


To run the MapR Streams Java consumer, run the Java consumer with the topic to read from:

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgConsumer /user/user01/stream:uberp 

_________________________________________________________________________

If you want to skip the machine learning and publishing part, the JSON results are in a file in the directory data/ubertripclusters.json
scp this file to /user/user01/data/ubertripclusters.json

Then run the MapR Streams Java producer to produce messages with the Java producer with the topic and data file arguments:

java -cp mapr-sparkml-streaming-uber-1.0.jar:`mapr classpath` com.streamskafka.uber.MsgProducer /user/user01/stream:uberp /user/user01/data/ubertripclusters.json

The you can  Run the Spark Consumer which consumes the machine learning enriched methods, run the spark consumer with the topic to read from and write to:

spark-submit --class com.sparkkafka.uber.SparkKafkaConsumer --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:uberp

/opt/mapr/spark/spark-2.0.1/bin/spark-submit --class com.sparkkafka.uber.V09DirectKafkaWordCount --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:ubers

spark-submit --class com.sparkkafka.uber.KafkaProducerExample  --master local[2] mapr-sparkml-streaming-uber-1.0.jar /user/user01/stream:ubert

maprcli stream topic delete -path /user/user01/stream -topic sensor ubers


By default, Spark classpath doesn't contain  "spark-streaming-kafka-0-9_2.11.jar".  
There are 3 ways:
1. Build your application with spark-streaming-kafka-0-9_2.11.jar as dependencies. 
2. Manually download spark-streaming-kafka-0-9_2.11.jar and put to SparkHome/jars folder.
3. Manually download spark-streaming-kafka-0-9_2.11.jar and  pass path to spark submit script with --jars option