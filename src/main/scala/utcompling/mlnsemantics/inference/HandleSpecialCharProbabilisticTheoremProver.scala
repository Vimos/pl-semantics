package utcompling.mlnsemantics.inference

import edu.mit.jwi.item.POS
import scala.collection.JavaConversions._
import scala.collection.mutable.SetBuilder
import utcompling.mlnsemantics.inference.support.HardWeightedExpression
import utcompling.mlnsemantics.inference.support.WeightedExpression
import utcompling.mlnsemantics.vecspace.BowVector
import utcompling.mlnsemantics.wordnet.Wordnet
import utcompling.scalalogic.discourse.candc.boxer.expression._
import utcompling.mlnsemantics.inference.support.SoftWeightedExpression
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.CollectionUtil._
import org.apache.commons.logging.LogFactory
import support.HardWeightedExpression

class HandleSpecialCharProbabilisticTheoremProver(
  delegate: ProbabilisticTheoremProver[BoxerExpression])
  extends ProbabilisticTheoremProver[BoxerExpression] {

  private val LOG = LogFactory.getLog(classOf[HandleSpecialCharProbabilisticTheoremProver])

  override def prove(
    constants: Map[String, Set[String]],
    declarations: Map[BoxerExpression, Seq[String]],
    evidence: List[BoxerExpression],
    assumptions: List[WeightedExpression[BoxerExpression]],
    goal: BoxerExpression): Option[Double] = {

    val specialChars = """([\?!\";\|\[\].,'_<>:\+\*-/&\^%\$#@~`=\(\)\\])""".r;

    def clearName(name: String): String =
      {
        var mlnId = specialChars.replaceAllIn(name, "_"); //remove all scpecial characeters. 
        mlnId = mlnId.map(c=> {
          if (c.toShort> 127) 'X' //remove non-ascii characters 
          else c;
        })
        return mlnId;
      }
    
    def go(e: BoxerExpression): BoxerExpression = {
      e match {
        case BoxerPred(discId, indices, variable, name, pos, sense) =>
          BoxerPred(discId, indices, variable, clearName(name), pos, sense)
        case BoxerRel(discId, indices, event, variable, name, sense) =>
          BoxerRel(discId, indices, event, variable, clearName(name), sense)          
        case BoxerNamed(discId, indices, variable, name, typ, sense) =>
          BoxerNamed(discId, indices, variable, clearName(name), typ, sense)
        case _ => e.visitConstruct(go)
      }
    }

    delegate.prove(constants, declarations, evidence, List(HardWeightedExpression(go(assumptions.head.expression))), go(goal))
  }
}