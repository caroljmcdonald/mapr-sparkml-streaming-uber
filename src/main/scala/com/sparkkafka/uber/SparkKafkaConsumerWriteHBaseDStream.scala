package com.sparkkafka.uber

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark._
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext._

import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql._
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName

import org.apache.hadoop.hbase.client.Put

import org.apache.hadoop.hbase.spark.HBaseContext
import org.apache.hadoop.hbase.util.Bytes

import org.apache.kafka.clients.consumer.ConsumerConfig

import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.kafka09.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import org.apache.spark.streaming.kafka.producer._
import org.apache.hadoop.hbase.spark.HBaseDStreamFunctions._
/*
see doc here
http://maprdocs.mapr.com/home/Spark/IntegrationSparkStreaming.html
 see example here
 https://github.com/mapr/hbase/blob/1.1.8-mapr-1703/hbase-spark/src/test/scala/org/apache/hadoop/hbase/spark/HBaseDStreamFunctionsSuite.scala
*/
object SparkKafkaConsumerWriteHBaseDStream {

  case class UberC(dt: String, lat: Double, lon: Double, cid: Integer, clat: Double, clon: Double, base: String) extends Serializable
  final val cfDataBytes = Bytes.toBytes("data")
  final val colLatBytes = Bytes.toBytes("lat")
  final val colLonBytes = Bytes.toBytes("lon")

  def convertToPut(uber: String): (Put) = {
    val uberp = JSONUtil.fromJson[UberC](uber)
    // create a composite row key: uberid_date time
    val rowkey = uberp.cid + "_" + uberp.base + "_" + uberp.dt
    val put = new Put(Bytes.toBytes(rowkey))
    // add to column family data, column data values to put object 
    put.addColumn(cfDataBytes, colLatBytes, Bytes.toBytes(uberp.lon))
    put.addColumn(cfDataBytes, colLonBytes, Bytes.toBytes(uberp.lat))
    return put
  }

  def main(args: Array[String]) = {
    if (args.length < 1) {
      System.err.println("Usage: SparkKafkaConsumerWriteHBase <topic consume> <mapr-db table> ")
      System.exit(1)
    }

    val groupId = "testgroup"
    val offsetReset = "earliest"
    val pollTimeout = "5000"
    var Array(topicc, tableName) = args
    val brokers = "maprdemo:9092" // not needed for MapR Streams

    val sparkConf = new SparkConf()
      .setAppName(SparkKafkaConsumer.getClass.getName)

    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, Seconds(2))

    val config = HBaseConfiguration.create()
    val hbaseContext = new HBaseContext(sc, config)

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
    // get the message value from message key value pair
    val valuesDStream = messagesDStream.map(_.value())

    System.out.println("received message stream")
    valuesDStream.count
    valuesDStream.print

    //convert each text message to an HBase Put and write to HBase

    valuesDStream.hbaseBulkPut(
      hbaseContext,
      TableName.valueOf(tableName),
      (putRecord) => {
        convertToPut(putRecord)
      }
    )

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
  }

}
