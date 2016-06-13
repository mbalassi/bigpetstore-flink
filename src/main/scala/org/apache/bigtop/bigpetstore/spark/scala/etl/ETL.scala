package org.apache.bigtop.bigpetstore.spark.scala.etl

import java.util.Date

import org.apache.bigtop.bigpetstore.flink.java.FlinkTransaction
import org.apache.bigtop.bigpetstore.flink.java.util.Utils
import org.apache.bigtop.bigpetstore.spark.scala._
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.JavaConversions._

object ETL {
  def main(args: Array[String]) {

    // parse input parameters
    val parameters = Utils.parseArgs(args)
    val numStores = parameters.getRequired("numStores").toInt
    val numCustomers = parameters.getRequired("numCustomers").toInt
    val simLength = parameters.getRequired("simLength").toDouble
    val input = parameters.getRequired("ETLInput")
    val output = parameters.getRequired("ETLOutput")
    val master = parameters.getRequired("master")

    deleteDir(output)

    // Initialize context
    val conf = new SparkConf(true).setMaster(master)
      .setAppName("DataGenerator")

    val sc = new SparkContext(conf)

    val transactions = sc.textFile(input)
      .map(new FlinkTransaction(_))

    // Generate unique product IDs
    val productsWithIndex = transactions.flatMap(t => t.getProducts)
      .distinct
      .zipWithUniqueId

    // Generate the customer-product pairs
    val customerAndProductIDS = transactions.flatMap(t => t.getProducts.map(p => (t.getCustomer.getId, p)))
      .map(pair => (pair._2, pair._1.toLong))
      .join(productsWithIndex)
      .map(res => (res._2._1, res._2._2))
      .distinct

//    customerAndProductIDS.saveAsTextFile(output)
    customerAndProductIDS.collect.foreach(println)

    //Print stats on the generated dataset
    val numProducts = productsWithIndex.count
    val filledFields = customerAndProductIDS.count
    val sparseness = 1 - (filledFields.toDouble / (numProducts * numCustomers))

    // Statistics with SparkSQL
    val sqlContext = new SQLContext(sc)

    val dataFrame = sqlContext.read.json(input)

    dataFrame.printSchema()

    val storeTransactionCount =

//    val table = transactions.map(toCaseClass(_)).toTable
//
//    // Transaction count of stores
//    val storeTransactionCount = table.groupBy('storeId).select('storeId, 'storeName, 'storeId.count as 'count)
//
//    // Store(s) with the most transactions
//    val bestStores = storeTransactionCount.select('count.max as 'max)
//      .join(storeTransactionCount)
//      .where("count = max")
//      .select('storeId, 'storeName, 'count)
//      .toDataSet[StoreCount].collect
//
//    // Transaction count of months
//    val monthTransactionCount = table.groupBy('month).select('month, 'month.count as 'count)
//      .toDataSet[MonthCount].collect
//
//    println("Generated bigpetstore stats")
//    println("---------------------------")
//    println("Customers:\t" + numCustomers)
//    println("Stores:\t\t" + numStores)
//    println("simLength:\t" + simLength)
//    println("Products:\t" + numProducts)
//    println("sparse:\t\t" + sparseness)
//    println()
//    println("Store(s) with the most transactions")
//    println("---------------------------")
//    bestStores.foreach(println(_))
//    println()
//    println("Monthly transaction count")
//    println("---------------------------")
//    monthTransactionCount.foreach(println(_))
  }

  // Type utilities for the Table API conversions
  case class Transaction(month : Int,
                         customerId : Int, customerName : (String, String),
                         transactionId : Int,
                         storeId : Int, storeName : String, storeCity : String,
                         products : Traversable[String])

  case class StoreCount(storeId : Int, storeName : String, count : Int)
  case class MonthCount(month : Int, count : Int)

  def toCaseClass(t : FlinkTransaction) = {
    val millis = (t.dateTime * 24 * 3600 * 1000).toLong
    val month = new Date(millis).getMonth
    Transaction(month, t.customer.getId, (t.customer.getName.getFirst, t.customer.getName.getFirst),
      t.id, t.store.getId, t.store.getName, t.store.getLocation.getCity, t.products)
  }
}
