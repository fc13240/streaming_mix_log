package com.asiainfo.mix.log.impl

import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.streaming.dstream.DStream
import com.asiainfo.mix.streaming_log.LogTools
import com.asiainfo.mix.streaming_log.StreamAction
import com.asiainfo.mix.xml.XmlProperiesAnalysis
import com.asiainfo.mix.streaming_log.DimensionEditor

/**
 * @author surq
 * @since 2014.07.15
 * 曝光日志 流处理
 */
class ExposeAnalysis extends StreamAction with Serializable {

  /**
   * @param inputStream:log流数据<br>
   */
  override def run(logtype: String, inputStream: DStream[Array[(String, String)]], logSteps: Int): DStream[Array[(String, String)]] = {
    printInfo(this.getClass(), "ExposeAnalysis is running!")

    val tablesMap = XmlProperiesAnalysis.getTablesDefMap
    val logPropertiesMaps = XmlProperiesAnalysis.getLogStructMap
    val logPropertiesMap = logPropertiesMaps(logtype)

    // log数据主key
    val keyItems = logPropertiesMap("rowKey").split(",")
    //输出为表结构样式　用
    val tbItems = tablesMap("items").split(",")
    // rowkey 连接符
    val separator = "asiainfoMixSeparator"

    inputStream.map(record => {
      val itemMap = record.toMap
      val keyMap = (for { key <- keyItems } yield (key, itemMap(key))).toMap
      (DimensionEditor.getExposeRowKeyEditor(keyMap, logSteps, separator), record)
    }).groupByKey.map(f => {

      val count = f._2.size
      val sumCost = for { f <- f._2; val map = f.toMap } yield (map("cost").toLong)

      // 创建db表结构并初始化
      var dbrecord = Map[String, String]()
      // 流计算
      dbrecord += (("bid_success_cnt") -> count.toString)
      dbrecord += (("expose_cnt") -> count.toString)
      dbrecord += (("cost") -> (sumCost.sum).toString)

      // 顺列流字段为db表结构字段,第一个字段rowKey
      var mesgae = LogTools.setTBSeq(f._1, tbItems, dbrecord)
      mesgae.toArray
    })
  }
}