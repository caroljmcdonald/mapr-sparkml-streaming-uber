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
      StructField("cluster", IntegerType, true),
      StructField("base", StringType, true)
    ))

    val spark = SparkSession.builder().appName("ClusterUber").getOrCreate()

    import spark.implicits._

    //  read json data 
    val df = spark.read.schema(schema).json("/user/user01/data/ubertripclusters.json").cache()

    df.printSchema

    df.show

    df.createOrReplaceTempView("uber")
    //
    df.select(month($"dt").alias("month"), dayofmonth($"dt").alias("day"), $"cluster").groupBy("month", "day", "cluster").agg(count("cluster").alias("count")).orderBy(desc("count")).show

    df.select(month($"dt").alias("month"), dayofmonth($"dt").alias("day"), hour($"dt").alias("hour"), $"cluster").groupBy("month", "day", "hour", "cluster").agg(count("cluster").alias("count")).orderBy(desc("count"), desc("cluster")).show

    df.select(month($"dt").alias("month"), dayofmonth($"dt").alias("day"), hour($"dt").alias("hour"), $"cluster").groupBy("month", "day", "hour", "cluster").agg(count("cluster").alias("count")).orderBy("day", "hour", "cluster").show

    df.groupBy("cluster").count().show()

    val countsDF = df.groupBy($"cluster", window($"dt", "1 hour")).count()
    countsDF.createOrReplaceTempView("uber_counts")

    spark.sql("select cluster, sum(count) as total_count from uber_counts group by cluster").show
    //spark.sql("select cluster, date_format(window.end, "MMM-dd HH:mm") as dt, count from uber_counts order by dt, cluster").show

    spark.sql("select cluster, count(cluster) as count from uber group by cluster").show

    spark.sql("SELECT hour(uber.dt) as hr,count(cluster) as ct FROM uber group By hour(uber.dt)").show
  }
}
