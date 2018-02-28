package com.sparkkafka.uber

import org.apache.kafka.clients.consumer.ConsumerConfig

import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql._

import org.apache.spark.streaming.{ Seconds, StreamingContext, Time }

import org.apache.spark.streaming.kafka09.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import org.apache.spark.streaming.kafka.producer._

/*

*/
object SparkKafkaConsumer {
case class UberC(dt: String, lat: Double, lon: Double, cid: Integer, clat: Double, clon: Double, base: String) extends Serializable
  def main(args: Array[String]) = {
    if (args.length ==2 ) {
      System.err.println("Usage: SparkKafkaConsumer <topic consume> ")
      System.exit(1)
    }

    val schema = StructType(Array(
      StructField("dt", TimestampType, true),
      StructField("lat", DoubleType, true),
      StructField("lon", DoubleType, true),
      StructField("cid", IntegerType, true),
      StructField("clat", DoubleType, true),
      StructField("clon", DoubleType, true),
      StructField("base", StringType, true)
    ))
    val groupId = "testgroup"
    val offsetReset = "earliest"
    val pollTimeout = "5000"
    val Array(topicc) = args
    val brokers = "maprdemo:9092" // not needed for MapR Streams, needed for Kafka

    val sparkConf = new SparkConf()
      .setAppName(SparkKafkaConsumer.getClass.getName)

    val ssc = new StreamingContext(sparkConf, Seconds(2))

    val topicsSet = topicc.split(",").toSet

    val kafkaParams = Map[String, String](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG ->
        "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG ->
        "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> offsetReset,
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false",
      "spark.kafka.poll.time" -> pollTimeout
    )

    val consumerStrategy = ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams)
    val messagesDStream = KafkaUtils.createDirectStream[String, String](
      ssc, LocationStrategies.PreferConsistent, consumerStrategy
    )

    val valuesDStream = messagesDStream.map(_.value())

    valuesDStream.foreachRDD { (rdd: RDD[String], time: Time) =>
      // There exists at least one element in RDD
      if (!rdd.isEmpty) {
        val count = rdd.count
        println("count received " + count)
        val spark = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
        import spark.implicits._

        import org.apache.spark.sql.functions._
        val df: Dataset[UberC] = spark.read.schema(schema).json(rdd).as[UberC]
        df.show
        df.createOrReplaceTempView("uber")

  //      df.groupBy("cid").count().show()

        spark.sql("select cid, count(cid) as count from uber group by cid").show

   //     spark.sql("SELECT hour(uber.dt) as hr,count(cid) as ct FROM uber group By hour(uber.dt)").show

        val countsDF = df.groupBy($"cid", window($"dt", "1 hour")).count()
        countsDF.createOrReplaceTempView("uber_counts")

   //     spark.sql("select cid, sum(count) as total_count from uber_counts group by cid").show
   
    //    spark.sql("select cid, count(cid) as count from uber group by cid").show

        spark.sql("SELECT hour(uber.dt) as hr,count(cid) as ct FROM uber group By hour(uber.dt)").show
      }
    }

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
  }

}
