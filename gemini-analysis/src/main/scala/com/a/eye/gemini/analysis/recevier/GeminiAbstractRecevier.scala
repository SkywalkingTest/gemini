package com.a.eye.gemini.analysis.recevier

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.CanCommitOffsets
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Assign
import org.apache.spark.streaming.kafka010.HasOffsetRanges
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.OffsetRange
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.ZooDefs.Ids

import com.a.eye.gemini.analysis.config.KafkaConfig
import com.a.eye.gemini.analysis.config.RedisConfig
import com.a.eye.gemini.analysis.config.SparkConfig
import com.a.eye.gemini.analysis.util.RedisClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.LogManager
import kafka.message.MessageAndMetadata
import org.apache.spark.SparkException
import org.apache.spark.rdd.RDD
import com.a.eye.gemini.analysis.util.OffsetsManager
import com.a.eye.gemini.analysis.executer.GeminiAnalysis
import com.a.eye.gemini.analysis.config.GeminiConfig
import com.a.eye.gemini.analysis.util.DateUtil
import com.a.eye.gemini.analysis.executer.model.RecevierPairsData
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkContext
import com.a.eye.gemini.analysis.util.SparkContextSingleton
import com.a.eye.gemini.analysis.executer.model.RecevierData

abstract class GeminiAbstractRecevier(appName: String, topicName: String, partitions: Int, groupId: String) extends Serializable {

  private val logger = LogManager.getFormatterLogger(this.getClass.getName)

  private def initialize[K, V](): (InputDStream[ConsumerRecord[Long, Array[Byte]]], StreamingContext) = {
    val sparkConf = SparkConfig.sparkConf.setAppName(appName)
    val streamingContext = new StreamingContext(sparkConf, Seconds(GeminiConfig.intervalTime))
    val kafkaParams = KafkaConfig.kafkaParams + ("group.id" -> groupId)
    val topics = Array(topicName)

    SparkContextSingleton.sparkContext = streamingContext.sparkContext

    val fromOffsets = OffsetsManager.selectOffsets(topicName, partitions).map { resultSet =>
      new TopicPartition(resultSet("topic"), resultSet("partition").toInt) -> resultSet("offset").toLong
    }.toMap

    (KafkaUtils.createDirectStream[Long, Array[Byte]](
      streamingContext,
      PreferConsistent,
      Assign[Long, Array[Byte]](fromOffsets.keys.toList, kafkaParams, fromOffsets)), streamingContext)
  }

  def startRecevie() {
    val logger = LogManager.getFormatterLogger(this.getClass.getName)
    val init = initialize()
    val streamDS = init._1
    val streamingContext = init._2

    streamDS.foreachRDD(rdd => {
      val offsets = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      val periodTime = DateUtil.date2String(new Date().getTime)
      logger.info("本次处理的消息条数： " + rdd.count())
      offsets.foreach { x => logger.info("本次消息的偏移量：从" + x.fromOffset + " 到 " + x.untilOffset) }
      val recordMapData = buildData(streamingContext, rdd, periodTime)
      GeminiAnalysis.startAnalysis(recordMapData, periodTime)
      offsets.foreach { x => OffsetsManager.persistentOffsets(x.topic, x.partition, x.untilOffset) }
    })
    streamingContext.start()
    streamingContext.awaitTermination()
  }

  def buildData(streamingContext: StreamingContext, rdd: RDD[ConsumerRecord[Long, Array[Byte]]], periodTime: String): RDD[(Long, Map[String, String])]
}