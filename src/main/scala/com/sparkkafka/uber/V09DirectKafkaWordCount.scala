/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package com.sparkkafka.uber

import org.apache.kafka.clients.consumer.ConsumerConfig

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.kafka09.{ ConsumerStrategies, KafkaUtils, LocationStrategies }

/**
 * Consumes messages from one or more topics in Kafka and does wordcount.
 * Usage: v09DirectKafkaWordCount <brokers> <topics>
 *   <brokers> is a list of one or more Kafka brokers
 *   <topics> is a list of one or more kafka topics to consume from
 *   <groupId> is the name of kafka consumer group
 *   <auto.offset.reset> What to do when there is no initial offset in Kafka or
 *                       if the current offset does not exist any more on the server
 *                       earliest: automatically reset the offset to the earliest offset
 *                       latest: automatically reset the offset to the latest offset
 *   <batch interval> is the time interval at which streaming data will be divided into batches
 *   <pollTimeout> is time, in milliseconds, spent waiting in Kafka consumer poll
 *                 if data is not available
 * Example:
 *    $ bin/run-example streaming.v09DirectKafkaWordCount broker1-host:port,broker2-host:port \
 *    topic1,topic2 my-consumer-group latest batch-interval pollTimeout
 */

object V09DirectKafkaWordCount {
  def main(args: Array[String]) {

    val groupId = "testgroup"
    val offsetReset = "earliest"
    val pollTimeout = "5000"
    val Array(topics) = args
    val brokers = "maprdemo:9092"

    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("v09DirectKafkaWordCount")
    val ssc = new StreamingContext(sparkConf, Seconds(2))
    ssc.checkpoint("~/tmp")
    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
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
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc, LocationStrategies.PreferConsistent, consumerStrategy
    )

    // Get the lines, split them into words, count the words and print
    val lines = messages.map(_.value())
    val words = lines.flatMap(_.split(" "))
    val wordCounts = words.map(x => (x, 1L)).reduceByKey(_ + _)
    wordCounts.print()

    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }
}
// scalastyle:on println
