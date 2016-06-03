package utcompling.mlnsemantics.vecspace

import org.apache.commons.logging.LogFactory
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.CollectionUtil._
import opennlp.scalabha.util.FileUtils._
import scala.collection.mutable.Buffer
import breeze.linalg.{DenseVector, SparseVector, Vector => BrVector}
import collection.mutable.{MutableList => MList, HashMap => MHashMap}
import util.Word2Vec

class BowVectorWithDistances(val self: BowVector) {
  def norm2: Double = self.norm(2)

  def normalized: BowVector = {
    val n2 = self.norm2
    if (n2 == 0) {
      self
    } else {
      self / n2
    }
  }

  def cosine(other: BowVector): Double = {
    self.normalized dot other.normalized
  }
  def euclid(other: BowVector): Double = {
    (self - other).norm2
  }
}

class DenseBowVector(vals: TraversableOnce[Double]) extends DenseVector[Double](vals.toArray, 0)

class SparseBowVector(vals: TraversableOnce[Double], indx: TraversableOnce[Int], numDims: Int) extends SparseVector[Double](indx.toArray, vals.toArray, numDims) {
  def this(numDims: Int) = this(Array[Double](), Array[Int](), numDims)
}



class BowVectorSpace(vectorMap: Map[String, BowVector]) {
  val numDims: Int = if (vectorMap.size == 0) 0; else vectorMap.head._2.length
  val zero: BowVector = new SparseBowVector(List[Double](), List[Int](), numDims)  //new DenseBowVector(new Array[Double](numDims))
  private val LOG = LogFactory.getLog(classOf[BowVectorSpace])
  private var word2vecSpace:Option[Word2Vec] = None;

  //a second constructor for Word2vec spaces 
  def this (word2vec:Word2Vec)
  {
  	this(Map[String, BowVector](("" -> new SparseBowVector(List[Double](), List[Int](), word2vec.dimSize)))); //map with one entry just to set numDims and zero
  	//val dimSize = word2vec.dimSize;
  
    this.word2vecSpace = Some(word2vec);
  }
  
  def get(word: String): Option[BowVector] = {
  	if (word2vecSpace.isDefined)
  	{
  		val vec:Array[Double] = word2vecSpace.get.getVec(word);
  		if (vec == null)
  			return None;
  		else return Some(new DenseBowVector(vec));
  		None;
  	}
  	else
  	{
	    val res = vectorMap.get(word)
	    res match {
	      case Some(v) => {}
	      case None => LOG.warn("Couldn't find '" + word + "'. Returning None.")
	    }
	    res
  	}
  }

  def getOrZero(word: String): BowVector = {
    get(word) match {
      case Some(v) => v
      case None => {
        LOG.warn("Couldn't find '" + word + "'. Returning zero vector.")
        zero
      }
    }
  }

}

object BowVectorSpace {
  def apply(filename: String): BowVectorSpace = {
  	  	//Binary space generated by word2vec
  	if (filename.endsWith(".bin"))
  		new BowVectorSpace(new Word2Vec(filename))
  	else
  		new BowVectorSpace(readSpace(filename))
  }

  def apply(filename: String, fltr: String => Boolean): BowVectorSpace = {
    val res = readSpace(filename).filter(sv => fltr(sv._1))
    new BowVectorSpace(res)
  }

  def nullVectorSpace: BowVectorSpace = {
    val singleton = new DenseBowVector(Array(1.0))
    val defaultMap = Map[String, BowVector]().withDefaultValue(singleton)
    new BowVectorSpace(defaultMap)
  }

  private def isDouble(s: String): Boolean = {
    try {
      s.toDouble
      true
    } catch {
      case nfe: java.lang.NumberFormatException => false
    }
  }
  def readSpace(filename: String): Map[String, BowVector] = {
    println("READING VS: ", filename)
    
    val reader = detectFormat(filename)
    val res = reader(filename)
    res
  }

  // auto-detects between the dhg format, a dense space and a sparse space
  // using the first line
  def detectFormat(filename: String): (String => Map[String, BowVector]) = {

    val lines = readLines(filename)
    if (!lines.hasNext)
    {
    	readDhgSpace _
    }
    else
    {
	  	val firstLine = lines.next
	    val Array(word, fields @ _*) = firstLine.split("\t", -1)
	    if (isDouble(fields(0))) {
	      // first field is a vector value. Definitely a dense space
	      readDenseSpace _
	    } else {
	      // not a dense space. either a dhg space or a sparse space
	      if (fields.length == 2) {
	        // only context and weight, definitely a sparse space
	        readSparseSpace _
	      } else {
	        readDhgSpace _
	      }
	    }
    }
  }
    
  def readDhgSpace(filename: String): Map[String, SparseBowVector] = {
    readLines(filename)
      .map(_.split("\t", -1))
      .collect {
        case Array(word, vector @ _*) => {
          val (dims, vals_s) = vector.grouped(2).map(_.toTuple2).unzip
          val (indx, vals) =
            vals_s.zipWithIndex.flatMap {
              case ("", i) => None
              case (v, i)  => Some((i, v.toDouble))
            }.sortBy(_._1).unzip
          (word, new SparseBowVector(vals, indx, dims.size))
        }
      }
      .toMap
  }

  private def changeExtension(filename: String, newExtension: String): String = {
    val position = filename.lastIndexOf('.')
    if (position < 0)
      filename + "." + newExtension
    else
      filename.substring(0, position) + newExtension
  }


  def readSparseColumns(filename: String): Map[String, Int] = {
      try {
        val colFilename = changeExtension(filename, ".cols")
        if (colFilename == filename)
          throw new java.io.IOException("Can't find cols file.")
        readLines(colFilename).zipWithIndex.toMap
      } catch {
        case e: java.io.IOException => {
          readLines(filename).map(_.split("\t")).map(_(1)).toSet.zipWithIndex.toMap
        }
      }
  }

  def readSparseSpace(filename: String): Map[String, SparseBowVector] = {
    val dim2index: Map[String,Int] = readSparseColumns(filename)

    val vectors = MHashMap[String, MList[(Int, Double)]]()

    readLines(filename)
      .map(_.split("\t"))
      .foreach{ case Array(word, dim, value_s) => {
        val value = value_s.toDouble
        if (!vectors.contains(word)) {
          vectors(word) = new MList
        }
        vectors(word) += ((dim2index(dim), value_s.toDouble))
      }}

    vectors
      .mapValues { dim_val_pairs => {
        val (indx, vals) = dim_val_pairs.sortBy(_._1).unzip
        new SparseBowVector(vals, indx, dim2index.size)
      }}
      .toMap
  }

  def readDenseSpace(filename: String): Map[String, DenseBowVector] = {
    readLines(filename)
      .map(_.split("\t"))
      .collect { case Array(word, vector @ _*) => (word, new DenseBowVector(vector.map(_.toDouble))) }
      .toMap
  }
  
  def main(args: Array[String])
  {
  	val vs = BowVectorSpace("../../GoogleNews-vectors-negative300.bin");
  	val vMan = vs.get("man");
  	val vWoman = vs.get("woman");
  	val vCar = vs.getOrZero("carrrrrr");
  	
  	println(vMan.get cosine vWoman.get)
  	println(vMan.get cosine vCar)
  }

  
}

