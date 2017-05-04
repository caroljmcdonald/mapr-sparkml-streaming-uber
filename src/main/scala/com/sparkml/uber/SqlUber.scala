/*

 */
package com.sparkml.uber
import org.apache.spark._

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql._


object SqlUber {

  def main(args: Array[String]) {

    val schema = StructType(Array(
      StructField("dt", TimestampType, true),
      StructField("lat", DoubleType, true),
      StructField("lon", DoubleType, true),
      StructField("cid", IntegerType, true),
      StructField("base", StringType, true)
    ))

    val spark = SparkSession.builder().appName("ClusterUber").getOrCreate()

    import spark.implicits._

    //  read json data 
    val df = spark.read.schema(schema).json("/user/user01/data/uber.json").cache()

    df.printSchema

    df.show

    df.createOrReplaceTempView("uber")
    //
    df.select(month($"dt").alias("month"), dayofmonth($"dt").alias("day"), $"cid").groupBy("month", "day", "cid").agg(count("cid").alias("count")).orderBy(desc("count")).show

    df.select(month($"dt").alias("month"), dayofmonth($"dt").alias("day"), hour($"dt").alias("hour"), $"cid").groupBy("month", "day", "hour", "cid").agg(count("cid").alias("count")).orderBy(desc("count"), desc("cid")).show

    df.select(month($"dt").alias("month"), dayofmonth($"dt").alias("day"), hour($"dt").alias("hour"), $"cid").groupBy("month", "day", "hour", "cid").agg(count("cid").alias("count")).orderBy("day", "hour", "cid").show

    df.groupBy("cid").count().show()

    val countsDF = df.groupBy($"cid", window($"dt", "1 hour")).count()
    countsDF.createOrReplaceTempView("uber_counts")

    spark.sql("select cid, sum(count) as total_count from uber_counts group by cid").show
    //spark.sql("select cid, date_format(window.end, "MMM-dd HH:mm") as dt, count from uber_counts order by dt, cid").show

    spark.sql("select cid, count(cid) as count from uber group by cid").show

    spark.sql("SELECT hour(uber.dt) as hr,count(cid) as ct FROM uber group By hour(uber.dt)").show
  }
}
