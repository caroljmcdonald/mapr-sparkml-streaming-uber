package com.sparkkafka.uber

// http://maprdocs.mapr.com/home/Spark/Spark_IntegrateMapRStreams.html

import org.apache.spark._

import org.apache.spark.SparkContext._
import org.apache.spark.streaming._

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.kafka09.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.producer._
import org.apache.kafka.common.serialization.StringSerializer

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql._

import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.clustering.KMeansModel

import org.apache.spark.rdd.RDD

object SparkKafkaConsumerProducer extends Serializable {

  import org.apache.spark.streaming.kafka.producer._
  // schema for uber data   
  case class Uber(dt: String, lat: Double, lon: Double, base: String) extends Serializable

  def parseUber(str: String): Uber = {
    val p = str.split(",")
    Uber(p(0), p(1).toDouble, p(2).toDouble, p(3))
  }

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      throw new IllegalArgumentException("You must specify the topics, for example  /user/user01/stream:ubers /user/user01/stream:uberp ")
    }

    val Array(topics, topicp) = args
    System.out.println("Subscribed to : " + topics)

    val brokers = "maprdemo:9092" // not needed for MapR Streams, needed for Kafka
    val groupId = "sparkApplication"
    val batchInterval = "2"
    val pollTimeout = "10000"

    val sparkConf = new SparkConf().setAppName("UberStream")

    val ssc = new StreamingContext(sparkConf, Seconds(batchInterval.toInt))
    val producerConf = new ProducerConf(
      bootstrapServers = brokers.split(",").toList
    )
    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, String](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG ->
        "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG ->
        "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "true",
      "spark.kafka.poll.time" -> pollTimeout,
      "spark.streaming.kafka.consumer.poll.ms" -> "8192"
    )
    // load model for getting clusters
    val model = KMeansModel.load("/user/user01/data/savemodel")
    // print out cluster centers 
    model.clusterCenters.foreach(println)

    val consumerStrategy = ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams)
    val messagesDStream = KafkaUtils.createDirectStream[String, String](
      ssc, LocationStrategies.PreferConsistent, consumerStrategy
    )
    // get message values from key,value
    val valuesDStream: DStream[String] = messagesDStream.map(_.value())

    valuesDStream.foreachRDD { rdd =>

      // There exists at least one element in RDD
      if (!rdd.isEmpty) {
        val count = rdd.count
        println("count received " + count)
        val spark = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
        import spark.implicits._
        import org.apache.spark.sql.functions._
        import org.apache.spark.sql.types._

        val df = rdd.map(parseUber).toDF()
        // Display the top 20 rows of DataFrame
        println("uber data")
        df.show()

        // get features to pass to model
        val featureCols = Array("lat", "lon")
        val assembler = new VectorAssembler().setInputCols(featureCols).setOutputCol("features")
        val df2 = assembler.transform(df)
        // get cluster categories from  model
        val categories = model.transform(df2)
        categories.show
        categories.createOrReplaceTempView("uber")

        //convert results to JSON string to send to topic 
        val res = spark.sql("select dt, lat, lon, base, prediction as cluster from uber ")
        val tRDD: org.apache.spark.sql.Dataset[String] = res.toJSON

        val temp: RDD[String] = tRDD.rdd
        temp.sendToKafka[StringSerializer](topicp, producerConf)

        println("sending messages")
        temp.take(2).foreach(println)
      }
    }

    // Start the computation
    println("start streaming")
    ssc.start()
    // Wait for the computation to terminate
    ssc.awaitTermination()

  }

}