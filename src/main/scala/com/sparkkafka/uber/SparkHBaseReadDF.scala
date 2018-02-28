/*
 * This example reads a row of time series uber data
 * calculates the the statistics for the hz data 
 * and then writes these statistics to the stats column family
 *  
 * you can specify specific columns to return, More info:
 * http://hbase.apache.org/apidocs/org/apache/hadoop/hbase/mapreduce/TableInputFormat.html
 */

package com.sparkkafka.uber
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._

import org.apache.spark._
import org.apache.spark.sql.{ DataFrame, SQLContext }
import org.apache.spark.SparkConf
import org.apache.spark.sql.datasources.hbase.HBaseTableCatalog

object SparkHBaseReadDF {

  val cat = s"""{
                |"table":{"namespace":"default", "name":"/user/user01/db/uber"},
                |"rowkey":"key",
                |"columns":{
                |"key":{"cf":"rowkey", "col":"key", "type":"string"},
                |"lat":{"cf":"data", "col":"lat", "type":"double"},
                |"lon":{"cf":"data", "col":"lon", "type":"double"}
                |}
                |}""".stripMargin

  def main(args: Array[String]) {

    val sparkConf = new SparkConf().setAppName("HBaseTest")
    val spark = SparkSession.builder().appName("Uber").getOrCreate()
    import spark.implicits._

    val sqlContext = new SQLContext(spark.sparkContext)

    def withCatalog(cat: String): DataFrame = {
      sqlContext
        .read
        .options(Map(HBaseTableCatalog.tableCatalog -> cat))
        .format("org.apache.hadoop.hbase.spark")
        .load()
    }

    val df = withCatalog(cat)
    println("show dataframe")
    df.show
    df.schema
    println("show for clusters with ID   < 11 ")
    df.filter($"key" <= "11")
      .select($"key", $"lat", $"lon").show

  }

}
