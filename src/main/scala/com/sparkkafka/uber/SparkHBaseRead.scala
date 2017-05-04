/*
 * This example reads a row of time series uber data
 * calculates the the statistics for the hz data 
 * and then writes these statistics to the stats column family
 *  
 * you can specify specific columns to return, More info:
 * http://hbase.apache.org/apidocs/org/apache/hadoop/hbase/mapreduce/TableInputFormat.html
 */

package com.sparkkafka.uber

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.filter.PrefixFilter
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark._
import org.apache.spark.SparkConf
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.spark.HBaseContext
import org.apache.hadoop.hbase.{ TableName, HBaseConfiguration }
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.client.Scan
import org.apache.spark.sql._
import java.lang.String
import org.apache.hadoop.hbase.client.Result

import org.apache.hadoop.hbase.client._
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import org.apache.spark.rdd.RDD

object SparkHBaseRead {

  case class UberRow(rowkey: String, lat: Double, lon: Double) extends Serializable
  val cfDataBytes = Bytes.toBytes("data")

  def parseUberRow(result: Result): UberRow = {
    val rowkey = Bytes.toString(result.getRow())
    val p1 = Bytes.toDouble(result.getValue(cfDataBytes, Bytes.toBytes("lat")))
    val p2 = Bytes.toDouble(result.getValue(cfDataBytes, Bytes.toBytes("lon")))
    UberRow(rowkey, p1, p2)
  }

  def main(args: Array[String]) {

    if (args.length < 1) {
      System.err.println("Usage: SparkHBaseRead <table path> ")
      System.exit(1)
    }
    val Array(tableName) = args
    val sparkConf = new SparkConf().setAppName("HBaseTest")
    val spark = SparkSession.builder().appName("Uber").getOrCreate()
    import spark.implicits._

    val conf = HBaseConfiguration.create()
    val hbaseContext = new HBaseContext(spark.sparkContext, conf)

    // create a filter to only read rows from cluster id 2 
    val filter: Filter = new PrefixFilter(Bytes.toBytes("2"));

    val scan: Scan = new Scan()
    scan.setFilter(filter);

    // Load an RDD of rowkey, result tuples from the table
    val hBaseRDD = hbaseContext.hbaseRDD(TableName.valueOf(tableName), scan)

    hBaseRDD.count()

    // get results
    val resultRDD: RDD[Result] = hBaseRDD.map(tuple => tuple._2)

    resultRDD.count()
    // parse result object into uber object
    val uberRDD: RDD[UberRow] = resultRDD.map(parseUberRow)

    uberRDD.take(3).foreach(uberRow => println("rowkey: " + uberRow.rowkey + " lat: " + uberRow.lat + " lon:" + uberRow.lon))

    val uberDF = uberRDD.toDF()
    // Return the schema of this DataFrame
    uberDF.printSchema()
    // Display the top 20 rows of DataFrame
    uberDF.show()
  }

}
